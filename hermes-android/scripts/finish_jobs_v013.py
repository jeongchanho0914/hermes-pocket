from pathlib import Path
p=Path(__file__).with_name('integrate_jobs_v013.py')
s=p.read_text()
header=s[:s.index('# Share schema')]
start=s.index("change('AgentRuntime.java','    void stopAll()")
tail=s[start:]
tail=tail.replace("'    void stopAll(){stop.set(true);'", "'    void stopAll(){\\n        stop.set(true);'")
tail=tail.replace("'    void stopAll(){jobs.cancelAll();stop.set(true);'", "'    void stopAll(){\\n        jobs.cancelAll();stop.set(true);'")
exec(compile(header+tail,str(p),'exec'))
