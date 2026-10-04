"""ADB helpers for APK verification on an explicitly selected Android emulator."""
import argparse, subprocess, xml.etree.ElementTree as ET, re, time
from pathlib import Path

class AndroidUI:
    def __init__(self,serial='emulator-5554'):
        if not serial.startswith('emulator-'):raise ValueError('Verification is restricted to an emulator, never a physical device.')
        self.serial=serial
    def adb(self,*args,binary=False):
        return subprocess.check_output(['adb','-s',self.serial,*args],text=not binary)
    def nodes(self):
        self.adb('shell','uiautomator','dump','/sdcard/hermes-window.xml')
        raw=self.adb('shell','cat','/sdcard/hermes-window.xml')
        return list(ET.fromstring(raw).iter('node'))
    def list(self):
        for n in self.nodes():
            a=n.attrib
            if a.get('text') or a.get('content-desc') or a.get('class')=='android.widget.EditText':
                print(a.get('class',''),repr(a.get('text')),repr(a.get('content-desc')),a.get('bounds'),a.get('clickable'),a.get('focused'))
    def tap(self,text):
        found=[n for n in self.nodes() if n.attrib.get('text')==text or n.attrib.get('content-desc')==text]
        if not found:raise LookupError(text)
        n=found[0]; b=list(map(int,re.findall(r'\d+',n.attrib['bounds']))); self.adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.4)
    def screenshot(self,path):Path(path).write_bytes(self.adb('exec-out','screencap','-p',binary=True))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['list','tap','screenshot']);p.add_argument('value',nargs='?');p.add_argument('--serial',default='emulator-5554');a=p.parse_args();ui=AndroidUI(a.serial)
    if a.action=='list':ui.list()
    elif a.action=='tap':ui.tap(a.value)
    else:ui.screenshot(a.value)
