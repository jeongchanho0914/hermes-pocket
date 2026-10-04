#!/usr/bin/env python3
"""Collect only Hermes' bounded, content-free failure records; never logcat or preferences."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'dev.chanho.hermes'
AUTHORIZED_SERIAL = 'R3CX20QTRPD'
MAX_BYTES = 256 * 1024
MAX_RECORDS = 256
PHASES = {'startup', 'runtime', 'model_connection', 'model_stream', 'tool', 'bridge',
          'ui', 'overlay', 'accessibility', 'screen_capture', 'terminal', 'skills', 'storage', 'other'}
IDENTIFIER = re.compile(r'[A-Za-z0-9_.$<>-]*\Z')
HEX = re.compile(r'[0-9a-f]{64}\Z')


def identifier(value, limit):
    if not isinstance(value, str) or len(value) > limit or not IDENTIFIER.fullmatch(value):
        raise ValueError('Invalid diagnostic source identifier')
    if re.search(r'(?:sk[-_]|ghp_|gho_|github_pat_|AIza|AKIA|ASIA|xox[baprs]-|Bearer)', value, re.IGNORECASE) or re.search(r'[A-Za-z0-9_-]{48,}', value):
        return 'unknown'
    return value


def integer(value, minimum, maximum):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError('Invalid diagnostic numeric field')
    return value


def sanitize_record(raw):
    if not isinstance(raw, dict) or type(raw.get('schema')) is not int or raw.get('schema') != 1:
        raise ValueError('Unsupported diagnostics schema')
    record_id = raw.get('id', '')
    if not isinstance(record_id, str) or not HEX.fullmatch(record_id):
        raise ValueError('Invalid diagnostic record ID')
    if raw.get('phase') not in PHASES or raw.get('type') not in ('caught', 'uncaught', 'process_exit', 'browser_error'):
        raise ValueError('Invalid diagnostic phase or type')
    result = {'schema': 1, 'id': record_id,
              'versionCode': integer(raw.get('versionCode'), 1, 2100000000),
              'versionName': identifier(raw.get('versionName'), 32),
              'phase': raw['phase'], 'type': raw['type'],
              'firstSeen': integer(raw.get('firstSeen'), 0, 2**63-1),
              'lastSeen': integer(raw.get('lastSeen'), 0, 2**63-1),
              'count': integer(raw.get('count'), 1, 1000000000)}
    if result['firstSeen'] > result['lastSeen']:
        raise ValueError('Invalid diagnostic observation times')
    if result['type'] == 'browser_error':
        if result['phase'] != 'ui' or raw.get('code') not in ('webview_error', 'unhandled_rejection') or raw.get('source') not in ('app.js', 'index.html', 'unknown'):
            raise ValueError('Invalid browser error provenance')
        result.update({'code': raw['code'], 'source': raw['source'],
                       'line': integer(raw.get('line'), 0, 10000000),
                       'column': integer(raw.get('column'), 0, 10000000), 'severity': 'error'})
        signature = {key: result[key] for key in ('phase', 'type', 'code', 'source')}
        result['fingerprint'] = hashlib.sha256(json.dumps(signature, sort_keys=True).encode()).hexdigest()
        return result
    if result['type'] == 'process_exit':
        if result['phase'] != 'startup' or raw.get('versionMeaning') != 'observed_at_startup':
            raise ValueError('Invalid process exit provenance')
        reason = integer(raw.get('exitReason'), 4, 6)
        result.update({'versionMeaning': 'observed_at_startup', 'exitReason': reason,
                       'exitTimestamp': integer(raw.get('exitTimestamp'), 0, 2**63-1),
                       'pid': integer(raw.get('pid'), 0, 2147483647),
                       'severity': 'critical', 'fingerprint': record_id})
        return result
    causes = raw.get('exceptions')
    if not isinstance(causes, list) or not 1 <= len(causes) <= 4:
        raise ValueError('Invalid diagnostic exception chain')
    exceptions = []
    for cause in causes:
        if not isinstance(cause, dict):
            raise ValueError('Invalid diagnostic exception')
        frames = cause.get('frames')
        if not isinstance(frames, list) or len(frames) > 12:
            raise ValueError('Invalid diagnostic source frames')
        clean_frames = []
        for frame in frames:
            if not isinstance(frame, dict):
                raise ValueError('Invalid diagnostic frame')
            clean_frames.append({'class': identifier(frame.get('class'), 160),
                                 'method': identifier(frame.get('method'), 100),
                                 'source': identifier(frame.get('source'), 100),
                                 'line': integer(frame.get('line'), -2147483648, 2147483647)})
        exceptions.append({'class': identifier(cause.get('class'), 160), 'frames': clean_frames})
    result['exceptions'] = exceptions
    # Stable grouping for developers; resolution always binds the exact version-specific ID.
    signature = {'phase': result['phase'], 'type': result['type'],
                 'exceptions': [{'class': ex['class'], 'frames': [
                     {key: frame[key] for key in ('class', 'method', 'source')}
                     for frame in ex['frames']]} for ex in exceptions]}
    result['fingerprint'] = hashlib.sha256(json.dumps(signature, sort_keys=True,
                                                     separators=(',', ':')).encode()).hexdigest()
    result['severity'] = 'critical' if result['type'] == 'uncaught' else 'error'
    return result


def decode_events(data):
    if len(data) > MAX_BYTES:
        raise ValueError('Diagnostics exceeds 256 KiB')
    lines = [line for line in data.decode('utf-8', errors='strict').splitlines() if line.strip()]
    if len(lines) > MAX_RECORDS:
        raise ValueError('Diagnostics exceeds 256 records')
    records = [sanitize_record(json.loads(line)) for line in lines]
    if len({record['id'] for record in records}) != len(records):
        raise ValueError('Duplicate diagnostic record ID')
    return records


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def collect_offline(root, reason):
    """Explicit disconnected preparation; never substitutes an empty clean read."""
    root = Path(root)
    if not isinstance(reason, str) or not 20 <= len(reason.strip()) <= 500:
        raise ValueError('Offline diagnostics requires a concrete reason of 20–500 characters')
    version = json.loads((root / 'version.json').read_text(encoding='utf-8'))
    target = f"v{version['major']}.{version['minor']:02d}"
    now = dt.datetime.now(dt.timezone.utc)
    cached = []
    directory = root / 'docs/diagnostics'
    for path in sorted((directory / 'reports').glob('*.json')):
        if path.stat().st_size > 1024 * 1024:
            raise ValueError('Oversized cached diagnostics report')
        raw = path.read_bytes()
        prior = json.loads(raw)
        if prior.get('status') != 'offline':
            cached.append({'path': str(path.relative_to(root)), 'sha256': hashlib.sha256(raw).hexdigest()})
    report = {'schema': 1, 'targetVersion': target, 'collectedAt': now.isoformat(),
              'package': PACKAGE, 'source': 'app-private-diagnostics', 'status': 'offline',
              'records': [], 'freshDiagnosticsAvailable': False, 'offlineReason': reason.strip(),
              'cachedReports': cached, 'cachedReportsReviewed': False}
    digest = hashlib.sha256(json.dumps(report, sort_keys=True).encode()).hexdigest()[:12]
    path = directory / 'reports' / f"{target}-{now.strftime('%Y%m%dT%H%M%S%fZ')}-{digest}.json"
    write_json(path, report)
    write_json(directory / 'latest.json', report)
    print('Diagnostics: OFFLINE; fresh phone errors unavailable; cached reports require review; '
          + str(path.relative_to(root)), flush=True)
    return path


def complete_offline_review(path, preflight):
    """Called only after the same strict cached-error preflight used by online builds."""
    path = Path(path)
    report = json.loads(path.read_text(encoding='utf-8'))
    if report.get('status') != 'offline':
        return report
    report.update({'cachedReportsReviewed': True,
                   'cachedReviewCompletedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
                   'cachedPendingIds': list(preflight['pendingIds']),
                   'cachedCriticalIds': list(preflight['criticalIds']),
                   'criticalDeferral': preflight.get('criticalDeferral')})
    write_json(path, report)
    write_json(path.parent.parent / 'latest.json', report)
    return report


def collect(root=ROOT, runner=subprocess.run, offline_reason=None):
    root = Path(root)
    if offline_reason is not None:
        return collect_offline(root, offline_reason)
    config_path = root / '.toolchain/diagnostics-device.json'
    if not config_path.exists():
        return None
    config = json.loads(config_path.read_text(encoding='utf-8'))
    if set(config) != {'serial'} or config['serial'] != AUTHORIZED_SERIAL:
        raise ValueError('Diagnostics device must be the explicitly authorized physical phone')
    adb = root / '.toolchain/android-test/platform-tools/adb'
    if not adb.is_file():
        raise RuntimeError('Pinned diagnostics adb is unavailable')
    # The release app exposes only bounded sanitized metadata through a read-only,
    # DUMP-permission + UID-checked provider; run-as is unavailable in release APKs.
    authority = b'dev.chanho.hermes.diagnostics'
    command = [str(adb), '-s', config['serial'], 'shell', 'content', 'read', '--uri',
               'content://dev.chanho.hermes.diagnostics/events']
    result = runner(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15, check=False)
    diagnostic_error = result.stdout + result.stderr
    missing_provider = (authority in diagnostic_error and
                        any(marker in diagnostic_error for marker in
                            (b'Could not find provider:', b'No content provider')))
    if missing_provider:
        status, records = 'not_available', []
    elif result.returncode:
        # Do not persist command errors: they are outside the trusted metadata schema.
        raise RuntimeError('App diagnostics collection failed; check phone connection and provider permission')
    else:
        status, records = 'collected', decode_events(result.stdout)
    version = json.loads((root / 'version.json').read_text(encoding='utf-8'))
    target = f"v{version['major']}.{version['minor']:02d}"
    now = dt.datetime.now(dt.timezone.utc)
    report = {'schema': 1, 'targetVersion': target, 'collectedAt': now.isoformat(),
              'package': PACKAGE, 'source': 'app-private-diagnostics', 'status': status, 'records': records}
    digest = hashlib.sha256(json.dumps(report, sort_keys=True).encode()).hexdigest()[:12]
    directory = root / 'docs/diagnostics'
    path = directory / 'reports' / f"{target}-{now.strftime('%Y%m%dT%H%M%S%fZ')}-{digest}.json"
    write_json(path, report)
    write_json(directory / 'latest.json', report)
    print(f'Diagnostics: {status}; {len(records)} records; {path.relative_to(root)}', flush=True)
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--offline-diagnostics', metavar='REASON', help='Explicit disconnected report; cached critical errors still block preparation')
    args = parser.parse_args()
    try:
        report = collect(offline_reason=args.offline_diagnostics)
        if args.offline_diagnostics is not None:
            from diagnostics_review import preflight
            complete_offline_review(report, preflight(ROOT))
        if report is None:
            print('Diagnostics collection not configured; existing reports will still be reviewed.')
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit(str(error))


if __name__ == '__main__':
    main()
