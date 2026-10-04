#!/usr/bin/env python3
"""Production diagnostic helper against explicit real-file host Android adapters."""
from pathlib import Path
import json,os,subprocess
ROOT=Path(__file__).resolve().parents[1]
def main():
 config=json.loads((ROOT/'.toolchain/paths.json').read_text());java=str(Path(config['java_home'])/'bin/java');classes=ROOT/'build/diagnostic-jvm-tests';classes.mkdir(parents=True,exist_ok=True);jar=ROOT/'.toolchain/testing/json-20240303.jar';source=ROOT/'app/src/main/java/dev/chanho/hermes';files=[source/'Diagnostics.java',source/'BuildConfig.java',*sorted((ROOT/'tests/diagnostic_jvm').glob('*.java'))]
 subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),*map(str,files)],check=True,cwd=ROOT)
 subprocess.run([java,'-cp',str(classes)+os.pathsep+str(jar),'dev.chanho.hermes.DiagnosticsHarness'],check=True,cwd=ROOT)
if __name__=='__main__':main()
