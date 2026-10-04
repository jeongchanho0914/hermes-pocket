"""Manifest declarations support narrowly typed user-confirmed Android activities."""
from pathlib import Path
import unittest,xml.etree.ElementTree as ET
class PhoneManifestTest(unittest.TestCase):
 def test_release_diagnostics_endpoint_requires_dump_permission_and_never_enables_debuggable_or_uri_write_grants(self):
  root=ET.parse(Path(__file__).resolve().parents[1]/'app/src/main/AndroidManifest.xml').getroot();a='{http://schemas.android.com/apk/res/android}';app=root.find('application')
  self.assertEqual(app.get(a+'debuggable'),'false')
  provider=next(p for p in app.findall('provider') if p.get(a+'authorities')=='dev.chanho.hermes.diagnostics')
  self.assertEqual(provider.get(a+'readPermission'),'android.permission.DUMP')
  self.assertEqual(provider.get(a+'grantUriPermissions'),'false')
  self.assertEqual(provider.get(a+'exported'),'true')
 def test_ordinary_intents_have_targeted_visibility_and_alarm_permission_without_call_or_send_authority(self):
  root=ET.parse(Path(__file__).resolve().parents[1]/'app/src/main/AndroidManifest.xml').getroot();a='{http://schemas.android.com/apk/res/android}'
  perms={p.get(a+'name') for p in root.findall('uses-permission')}
  self.assertIn('com.android.alarm.permission.SET_ALARM',perms)
  self.assertFalse(perms.intersection({'android.permission.CALL_PHONE','android.permission.SEND_SMS','android.permission.READ_SMS','android.permission.QUERY_ALL_PACKAGES'}))
  visible=set()
  for intent in root.findall('queries/intent'):
   action=intent.find('action').get(a+'name');data=intent.find('data')
   visible.add((action,data.get(a+'scheme') if data is not None else None,data.get(a+'mimeType') if data is not None else None))
  for action,scheme,mime in [('VIEW','http',None),('VIEW','https',None),('VIEW','geo',None),('SEND',None,'text/plain'),('SENDTO','mailto',None),('SENDTO','smsto',None),('DIAL','tel',None),('SET_ALARM',None,None)]:
   self.assertIn(('android.intent.action.'+action,scheme,mime),visible)
