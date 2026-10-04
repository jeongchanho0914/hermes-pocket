#!/usr/bin/env python3
"""Capture the real offline GUI for comparison; no phone or model is simulated."""
import os, shutil
from pathlib import Path
from playwright.sync_api import sync_playwright
from make_preview import DESIGNS, ROOT, bundle, write_previews

def capture():
    write_previews()
    executable=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium') or shutil.which('chromium-browser')
    with sync_playwright() as pw:
        browser=pw.chromium.launch(executable_path=executable,headless=True,args=['--no-sandbox'])
        for i,design in enumerate(DESIGNS,1):
            page=browser.new_page(viewport={'width':360,'height':800},device_scale_factor=2)
            errors=[];page.on('pageerror',lambda error: errors.append(str(error)))
            page.set_content(bundle(design));page.wait_for_timeout(100)
            page.screenshot(path=str(ROOT/'docs/designs'/f'{i:02d}-{design}.png'))
            if errors: raise RuntimeError(f'{design}: {errors}')
            page.close()
        browser.close()
    print(ROOT/'docs/design-gallery.html')

if __name__=='__main__': capture()
