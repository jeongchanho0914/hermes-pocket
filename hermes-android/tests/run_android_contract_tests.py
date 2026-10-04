#!/usr/bin/env python3
"""Compile real Android sources; execute only framework-free production validators."""
from pathlib import Path
import json,os,subprocess
ROOT=Path(__file__).resolve().parents[1]
def main():
 cfg=json.loads((ROOT/'.toolchain/paths.json').read_text());java=str(Path(cfg['java_home'])/'bin/java');out=ROOT/'build/android-contract-tests';out.mkdir(parents=True,exist_ok=True)
 jar=ROOT/'.toolchain/testing/json-20240303.jar';deps=sorted((ROOT/'.toolchain/shizuku-13.1.5').glob('*.jar'))
 if len(deps)!=5:raise RuntimeError('Prepared pinned Android dependencies required')
 build=out/'BuildConfig.java';build.write_text('package dev.chanho.hermes; final class BuildConfig { static final String VERSION_NAME="test"; static final int VERSION_CODE=0; static final String RELEASE_CHANNEL="test"; }')
 res=out/'R.java';res.write_text('package dev.chanho.hermes; final class R { static final class drawable { static final int ic_launcher=0; } }')
 cp=os.pathsep.join(map(str,[jar,Path(cfg['android_jar']),*deps]));sources=sorted((ROOT/'app/src/main/java').rglob('*.java'))+([build] if not (ROOT/'app/src/main/java/dev/chanho/hermes/BuildConfig.java').exists() else [])+[res]+sorted((ROOT/'tests/android_jvm').glob('*.java'))
 subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-cp',cp,'-d',str(out),*map(str,sources)],check=True,cwd=ROOT)
 subprocess.run([java,'-cp',str(out)+os.pathsep+cp,'dev.chanho.hermes.PhoneContractsHarness'],check=True,cwd=ROOT)
if __name__=='__main__':main()
