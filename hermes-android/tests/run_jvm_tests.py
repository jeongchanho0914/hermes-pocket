#!/usr/bin/env python3
"""Run production Net/DirectAgent against a local fixture, not a live provider.

Requires the isolated toolchain prepared by scripts/bootstrap_build.py. The
official org.json JVM artifact is checksum pinned; Android classes are replaced
only for storage and device capabilities. Nothing installs on a phone.
"""
from pathlib import Path
import hashlib
import json
import subprocess
import urllib.request
import argparse

ROOT = Path(__file__).resolve().parents[1]
JAR_SHA = '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--harness',action='append',help='Run only the named compiled harness (repeatable)')
    args=parser.parse_args()
    config = json.loads((ROOT / '.toolchain/paths.json').read_text())
    java = str(Path(config['java_home']) / 'bin/java')
    cache = ROOT / '.toolchain/testing'
    cache.mkdir(parents=True, exist_ok=True)
    jar = cache / 'json-20240303.jar'
    if not jar.exists():
        with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', timeout=60) as response:
            jar.write_bytes(response.read())
    if hashlib.sha256(jar.read_bytes()).hexdigest() != JAR_SHA:
        raise RuntimeError('org.json checksum mismatch')
    classes = ROOT / 'build/jvm-tests'
    classes.mkdir(parents=True, exist_ok=True)
    release = json.loads((ROOT / 'version.json').read_text())
    version = f"{release['major']}.{release['minor']:02d}"
    generated = classes / 'BuildConfig.java'
    generated.write_text('package dev.chanho.hermes; final class BuildConfig { static final String VERSION_NAME = '+json.dumps(version)+'; }\n')
    source = ROOT / 'app/src/main/java/dev/chanho/hermes'
    if args.harness == ['TerminalSessionsHarness']:
        sources=[str(source / 'TerminalSessions.java'),str(ROOT / 'tests/jvm/TerminalSessionsHarness.java')]
        subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(classes),*sources],check=True)
        subprocess.run([java,'-cp',str(classes),'dev.chanho.hermes.TerminalSessionsHarness'],check=True)
        return
    sources = [str(source / name) for name in ('OwnerApprovalPolicy.java', 'PhoneFilesPaths.java', 'MainThreadCall.java', 'Net.java', 'DirectAgent.java', 'LocalCapabilities.java', 'LocalSkillStore.java', 'LocalAgentTools.java', 'WebTools.java', 'J.java', 'ToolArgs.java', 'ObservationRequired.java', 'SemanticTarget.java', 'ExtensionSchemas.java', 'TaskLedger.java', 'TaskPool.java', 'WorkerAgent.java')]
    privilege_policy = source / 'PhonePrivilegePolicy.java'
    if privilege_policy.exists():
        sources.append(str(privilege_policy))
    for name in ('TerminalSessions.java','TerminalTools.java','BuiltinSkills.java','SkillPackages.java','BuiltinSkillLibrary.java','ModelLimits.java','ContextCompactor.java','MemoryDocuments.java'):
        if (source / name).exists():
            sources.append(str(source / name))
    sources += [str(p) for p in (ROOT / 'tests/jvm').glob('*.java')
                if p.name != 'PhonePrivilegePolicyHarness.java' or privilege_policy.exists()] + [str(generated)]
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), *sources], check=True)
    harnesses=args.harness or ['DirectAgentHarness','ScreenVisionHarness','ModelProgressHarness','TimelineSegmentsHarness','LocalFeaturesHarness','MainThreadCallHarness','PhoneFilesPathsHarness','OwnerApprovalPolicyHarness']+(['PhonePrivilegePolicyHarness'] if privilege_policy.exists() else [])+(['TerminalSessionsHarness','TerminalToolsHarness'] if (source / 'TerminalSessions.java').exists() else [])+(['SkillPackagesHarness','LocalSkillToolsHarness','ActivitySummaryHarness'] if (source / 'SkillPackages.java').exists() else [])+(['BuiltinSkillLibraryHarness'] if (source / 'BuiltinSkillLibrary.java').exists() else [])+(['ContextCompactorHarness'] if (ROOT / 'tests/jvm/ContextCompactorHarness.java').exists() else [])+(['MemoryDocumentsHarness'] if (ROOT / 'tests/jvm/MemoryDocumentsHarness.java').exists() else [])+(['JobsHarness'] if (ROOT / 'tests/jvm/JobsHarness.java').exists() else [])+(['SemanticTargetHarness'] if (ROOT / 'tests/jvm/SemanticTargetHarness.java').exists() else [])
    for harness in harnesses:
        subprocess.run([java, '-cp', str(classes)+':'+str(jar), 'dev.chanho.hermes.'+harness], check=True)

if __name__ == '__main__':
    main()
