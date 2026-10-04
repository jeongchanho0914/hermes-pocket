#!/usr/bin/env python3
"""Bundle our own app assets into a network-free, non-native UI preview.
This preview does not emulate a phone and never calls a provider or system tool.
"""
import base64,hashlib,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
DESIGNS=('white','black')
LABELS=('화이트','블랙')
def bundle(design=None):
    html=(ASSETS/'index.html').read_text()
    if design:
        if design not in DESIGNS: raise ValueError('Unknown design')
        html=html.replace('data-design="white"','data-design="'+design+'" data-preview-design="'+design+'"',1)
    css=(ASSETS/'app.css').read_text()
    logo='data:image/svg+xml;base64,'+base64.b64encode((ASSETS/'logo.svg').read_bytes()).decode()
    js=(ASSETS/'app.js').read_text().replace("'logo.svg'",repr(logo))+'\n;\n'+(ASSETS/'cli.js').read_text()
    css_hash=base64.b64encode(hashlib.sha256(css.encode()).digest()).decode()
    js_hash=base64.b64encode(hashlib.sha256(js.encode()).digest()).decode()
    policy="default-src 'none'; script-src 'sha256-"+js_hash+"'; style-src 'sha256-"+css_hash+"'; img-src data:; connect-src 'none'; font-src 'none'; base-uri 'none'; form-action 'none'"
    html=re.sub(r'<meta http-equiv="Content-Security-Policy" content="[^"]*">','<meta http-equiv="Content-Security-Policy" content="'+policy+'">',html)
    html=html.replace('<link rel="stylesheet" href="app.css">','<style>'+css+'</style>')
    html=html.replace('<script defer src="app.js"></script>','').replace('<script defer src="cli.js"></script>','')
    html=html.replace('src="logo.svg"','src="'+logo+'"')
    return html.replace('</body>','<script>'+js+'</script></body>')
def write_previews():
    docs=ROOT/'docs'
    output=docs/'designs';output.mkdir(parents=True,exist_ok=True)
    (docs/'preview.html').write_text(bundle())
    cards=[]
    for i,(design,label) in enumerate(zip(DESIGNS,LABELS),1):
        stem=f'{i:02d}-{design}'
        (output/(stem+'.html')).write_text(bundle(design))
        cards.append(f'<article><a class="preview" href="designs/{stem}.html"><img loading="lazy" src="designs/{stem}.png" alt="{design.title()} 실제 GUI 미리보기" width="360" height="800"></a><div class="card-copy"><span>{i:02d}</span><div><h2>{design.title()}</h2><p>{label}</p></div><a class="open" href="designs/{stem}.html" aria-label="{design.title()} 열기">↗</a></div></article>')
    css="""*{box-sizing:border-box}body{margin:0;background:#f4f3ef;color:#242724;font:14px -apple-system,BlinkMacSystemFont,Segoe UI,sans-serif}header{max-width:1440px;margin:auto;padding:64px 40px 36px}header small{font-size:11px;letter-spacing:.18em;color:#656d65}h1{font-size:clamp(32px,5vw,58px);font-weight:550;letter-spacing:-.055em;margin:16px 0}header p{max-width:720px;line-height:1.8;color:#656d65}header a{color:#242724}main{max-width:1440px;margin:auto;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:32px 22px;padding:0 40px 56px}article{min-width:0}.preview{display:block;border:1px solid #d4d8d0;border-radius:18px;overflow:hidden;background:#fff;box-shadow:0 12px 25px #172c1710;transition:transform .2s}.preview:hover{transform:translateY(-4px)}img{width:100%;height:auto;display:block}.card-copy{display:flex;align-items:center;gap:10px;padding:16px 2px}.card-copy>span{color:#797e75;font-size:11px;font-variant-numeric:tabular-nums}.card-copy h2{font-size:16px;font-weight:600;margin:0 0 5px}.card-copy p{font-size:11px;color:#626a62;margin:0;line-height:1.6}.open{margin-left:auto;display:grid;place-items:center;min-width:44px;min-height:44px;color:inherit;text-decoration:none;border:1px solid #d4d8d0;border-radius:50%;font-size:22px}footer{border-top:1px solid #d4d8d0;padding:24px 40px;color:#626a62;font-size:12px;line-height:1.8}a:focus-visible{outline:3px solid #3b6751;outline-offset:5px}@media(max-width:1100px){main{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:650px){header{padding:36px 22px 24px}main{padding:0 22px 36px;grid-template-columns:repeat(2,minmax(0,1fr));gap:22px 14px}.card-copy{gap:6px}.card-copy p{font-size:10px}.open{min-width:36px}.card-copy h2{font-size:14px}footer{padding:22px}}@media(prefers-reduced-motion:reduce){.preview{transition:none}}"""
    page='<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Hermes Pocket · White & Black</title><style>'+css+'</style><body><header><small>HERMES POCKET / DESIGN COLLECTION</small><h1>Android 채팅 화면<br>화이트와 블랙.</h1><p>모바일 채팅을 중심으로 다시 만든 화면입니다. 각 화면을 눌러 메뉴, 대화 입력, 기기 도구와 설정을 둘러보세요. 모든 스타일에서 같은 기능을 사용합니다.</p><p>브라우저에서는 화면을 비교할 수 있습니다. 실제 기기 제어·API 연결·저장은 Android APK에서 동작합니다. <a href="mobile-preview.html">휴대폰 크기로 열기 ↗</a></p></header><main>'+''.join(cards)+'</main><footer>외부 폰트·이미지·네트워크 요청 없이 열 수 있는 로컬 미리보기입니다. 앱의 설정 → 화면 디자인에서 2개 테마을 전환할 수 있습니다.</footer></body></html>'
    (docs/'design-gallery.html').write_text(page)
    return docs/'design-gallery.html'
if __name__=='__main__':
    print(write_previews())
