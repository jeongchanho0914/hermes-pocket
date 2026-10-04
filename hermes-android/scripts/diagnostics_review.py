#!/usr/bin/env python3
"""Review saved app errors with concrete source changes and test evidence before builds."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import sys
import shlex
import subprocess
from collect_diagnostics import ROOT, HEX, sanitize_record, write_json


class PendingCriticalError(RuntimeError):
    pass


def load_records(root=ROOT):
    root = Path(root)
    directory = root / 'docs/diagnostics/reports'
    records = {}
    for path in sorted(directory.glob('*.json')):
        if path.stat().st_size > 1024 * 1024:
            raise ValueError('Oversized diagnostics report: ' + path.name)
        report = json.loads(path.read_text(encoding='utf-8'))
        if report.get('schema') != 1 or report.get('package') != 'dev.chanho.hermes' or report.get('source') != 'app-private-diagnostics':
            raise ValueError('Unsupported diagnostics report: ' + path.name)
        raw_records = report.get('records')
        if not isinstance(raw_records, list) or len(raw_records) > 256:
            raise ValueError('Invalid diagnostics report records')
        for raw in raw_records:
            record = sanitize_record(raw)
            previous = records.get(record['id'])
            if previous is None or (record['lastSeen'], record['count']) > (previous['lastSeen'], previous['count']):
                records[record['id']] = record
    return records


def load_ledger(root=ROOT):
    path = Path(root) / 'docs/diagnostics/reviews.json'
    if not path.exists():
        return {'schema': 1, 'reviews': []}
    ledger = json.loads(path.read_text(encoding='utf-8'))
    if ledger.get('schema') != 1 or not isinstance(ledger.get('reviews'), list):
        raise ValueError('Unsupported diagnostics review ledger')
    return ledger


def checked_file(root, value, source=False):
    root = Path(root).resolve()
    path = (root / value).resolve()
    if not path.is_relative_to(root) or not path.is_file() or path.stat().st_size == 0:
        raise ValueError('Evidence must be an existing nonempty project file: ' + str(value))
    if path.stat().st_size > 8 * 1024 * 1024:
        raise ValueError('Evidence file is oversized')
    relative = str(path.relative_to(root))
    if relative.startswith(('.signing/', '.toolchain/', 'state/')):
        raise ValueError('Private files cannot be review evidence')
    if source and (path.suffix not in {'.java', '.js', '.css', '.html', '.xml', '.py', '.json'} or
                   not relative.startswith(('app/src/', 'scripts/'))):
        raise ValueError('Patch evidence must name application or build source files')
    return {'path': relative, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def verify_evidence(root, entries, sources, test_command):
    for entry in entries:
        manifest_path = Path(root) / entry['path']
        if manifest_path.suffix != '.json':
            raise ValueError('Review requires structured output from the verify command')
        manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
        if (type(manifest.get('schema')) is not int or manifest.get('schema') != 1 or manifest.get('tool') != 'hermes-diagnostics-verification'
                or manifest.get('exitCode') != 0 or type(manifest.get('exitCode')) is not int
                or not isinstance(manifest.get('command'), list)
                or manifest.get('command') != shlex.split(test_command)):
            raise ValueError('Test verification must record the actual supplied command and successful exit')
        if not isinstance(manifest.get('sources'), list) or not all(source in manifest['sources'] for source in sources):
            raise ValueError('Test verification does not bind the reviewed source hashes')
        output = manifest.get('output', {})
        if not isinstance(output, dict) or checked_file(root, output.get('path', '')) != output:
            raise ValueError('Test verification output is missing or changed')


def run_verification(root, sources, command, output, runner=subprocess.run):
    """Run a developer-supplied argv, never commands read from error records."""
    root = Path(root).resolve()
    if not sources or not command or any(not isinstance(part, str) or not part for part in command):
        raise ValueError('Verification requires explicit source paths and a real command argv')
    manifest_path = (root / output).resolve()
    if not manifest_path.is_relative_to(root / 'docs/diagnostics/verification') or manifest_path.suffix != '.json':
        raise ValueError('Verification result must be docs/diagnostics/verification/*.json')
    checked_sources = [checked_file(root, source, source=True) for source in sources]
    started = dt.datetime.now(dt.timezone.utc).isoformat()
    result = runner(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    timeout=1800, check=False)
    data = result.stdout
    if not isinstance(data, bytes) or not data or len(data) > 8 * 1024 * 1024:
        raise ValueError('Test must produce bounded nonempty output (at most 8 MiB)')
    if checked_sources != [checked_file(root, source, source=True) for source in sources]:
        raise ValueError('Reviewed source changed during verification; run it after edits finish')
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    log_path = manifest_path.with_suffix('.log')
    log_path.write_bytes(data)
    manifest = {'schema': 1, 'tool': 'hermes-diagnostics-verification',
                'command': command, 'exitCode': result.returncode, 'sources': checked_sources,
                'output': checked_file(root, str(log_path.relative_to(root))),
                'startedAt': started, 'completedAt': dt.datetime.now(dt.timezone.utc).isoformat()}
    write_json(manifest_path, manifest)
    if result.returncode != 0:
        raise ValueError('Verification failed; its saved result cannot resolve an error')
    return str(manifest_path.relative_to(root))


def review_is_current(root, review, record):
    if review.get('id') != record['id'] or review.get('fingerprint') != record['fingerprint']:
        return False
    if review.get('lastSeen', -1) < record['lastSeen'] or review.get('count', 0) < record['count']:
        return False
    if review.get('resolution') not in ('fixed', 'mitigated', 'external', 'notbug'):
        return False
    if record['severity'] == 'critical' and review.get('resolution') in ('external', 'notbug'):
        return False
    if not isinstance(review.get('summary'), str) or len(review['summary'].strip()) < 20:
        return False
    if not isinstance(review.get('testCommand'), str) or len(review['testCommand'].strip()) < 8:
        return False
    for category, source in [('sources', True), ('testEvidence', False)]:
        entries = review.get(category)
        if not isinstance(entries, list) or not entries:
            return False
        try:
            for entry in entries:
                actual = checked_file(root, entry['path'], source=source)
                if actual['sha256'] != entry['sha256']:
                    return False
        except (ValueError, KeyError, OSError, TypeError):
            return False
    try:
        verify_evidence(root, review['testEvidence'], review['sources'], review['testCommand'])
    except (ValueError, OSError, KeyError, TypeError):
        return False
    return True


def pending_records(root=ROOT):
    records = load_records(root)
    ledger = load_ledger(root)
    return [record for record in records.values()
            if not any(review_is_current(root, review, record) for review in ledger['reviews'])]


def preflight(root=ROOT, defer_critical=None):
    pending = pending_records(root)
    critical = [record for record in pending if record['severity'] == 'critical']
    for record in pending:
        if record['type'] == 'browser_error':
            print(f"PENDING error {record['id']} (UI {record['code']}, v{record['versionName']}): "
                  f"{record['source']}:{record['line']}:{record['column']}", flush=True)
            continue
        if record['type'] == 'process_exit':
            print(f"PENDING critical {record['id']} (OS exit reason {record['exitReason']}, "
                  f"observed at startup v{record['versionName']}; historical crash version unknown)", flush=True)
            continue
        first = record['exceptions'][0]
        frame = next((frame for ex in record['exceptions'] for frame in ex['frames']
                      if frame['class'].startswith('dev.chanho.hermes')), None)
        location = f"{frame['source']}:{frame['line']} {frame['method']}" if frame else 'source frame unavailable'
        print(f"PENDING {record['severity']} {record['id']} ({record['phase']}, v{record['versionName']}): "
              f"{first['class']} — {location}", flush=True)
    if critical:
        if not defer_critical or len(defer_critical.strip()) < 20:
            raise PendingCriticalError('Build blocked by unreviewed critical app crashes. Read docs/diagnostics/latest.json; '
                                       'fix and test, then record exact IDs with scripts/diagnostics_review.py review.')
        print('EXPLICIT CRITICAL DEFERRAL: ' + defer_critical, flush=True)
    return {'pendingIds': [record['id'] for record in pending],
            'criticalIds': [record['id'] for record in critical],
            'criticalDeferral': defer_critical if critical else None}


def record_review(root, ids, resolution, summary, sources, test_command, evidence):
    if resolution not in ('fixed', 'mitigated', 'external', 'notbug') or len(summary.strip()) < 20 or len(test_command.strip()) < 8:
        raise ValueError('Review requires a concrete patch summary and actual test command')
    if not ids or len(ids) != len(set(ids)) or any(not HEX.fullmatch(record_id) for record_id in ids):
        raise ValueError('Supply explicit, unique diagnostic IDs')
    if not sources or not evidence:
        raise ValueError('Review requires source paths and existing test-output evidence')
    checked_sources = [checked_file(root, path, source=True) for path in sources]
    checked_evidence = [checked_file(root, path) for path in evidence]
    if any(item['path'] in {source['path'] for source in checked_sources} for item in checked_evidence):
        raise ValueError('Test evidence must be a separate actual test-output file, not the patch source')
    if any(Path(item['path']).suffix not in {'.log', '.txt', '.json', '.xml'} for item in checked_evidence):
        raise ValueError('Test evidence must be an actual saved test result (.log/.txt/.json/.xml)')
    verify_evidence(root, checked_evidence, checked_sources, test_command)
    records = load_records(root)
    if any(record_id not in records for record_id in ids):
        raise ValueError('Review ID is absent from collected diagnostics')
    if resolution in ('external', 'notbug') and any(records[record_id]['severity'] == 'critical' for record_id in ids):
        raise ValueError('Uncaught crashes require an actual fix or mitigation, not external/notbug classification')
    ledger = load_ledger(root)
    now = dt.datetime.now(dt.timezone.utc).isoformat()
    for record_id in ids:
        record = records[record_id]
        ledger['reviews'].append({'id': record_id, 'fingerprint': record['fingerprint'],
                                  'lastSeen': record['lastSeen'], 'count': record['count'],
                                  'reviewedAt': now, 'resolution': resolution, 'summary': summary,
                                  'sources': checked_sources, 'testCommand': test_command,
                                  'testEvidence': checked_evidence})
    write_json(Path(root) / 'docs/diagnostics/reviews.json', ledger)
    return ids


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('pending')
    review = commands.add_parser('review')
    review.add_argument('--id', action='append', required=True, dest='ids')
    review.add_argument('--resolution', choices=['fixed', 'mitigated', 'external', 'notbug'], required=True)
    review.add_argument('--summary', required=True)
    review.add_argument('--source', action='append', required=True, dest='sources')
    review.add_argument('--test-command', required=True)
    review.add_argument('--test-evidence', action='append', required=True, dest='evidence')
    verification = commands.add_parser('verify')
    verification.add_argument('--source', action='append', required=True, dest='sources')
    verification.add_argument('--output', required=True)
    verification.add_argument('argv', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    try:
        if args.command == 'pending':
            pending = pending_records()
            print(json.dumps(pending, ensure_ascii=False, indent=2))
            return 2 if any(record['severity'] == 'critical' for record in pending) else 0
        if args.command == 'verify':
            command = args.argv[1:] if args.argv[:1] == ['--'] else args.argv
            result = run_verification(ROOT, args.sources, command, args.output)
            print(json.dumps({'verification': result, 'exitCode': 0}, indent=2))
            return 0
        ids = record_review(ROOT, args.ids, args.resolution, args.summary, args.sources,
                            args.test_command, args.evidence)
        print(json.dumps({'reviewedIds': ids, 'phoneRecordsDeleted': False}, indent=2))
        return 0
    except (ValueError, OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
