from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from transfer_craft_component_palette import transfer, ANDROID as A
from transfer_craft_fbd_menus import MENUS
ROOT=Path(__file__).resolve().parents[1]
class OriginalFbdEditorTests(unittest.TestCase):
 def test_source_context_and_timer_menus_preserve_every_row_and_attribute(self):
  for name in MENUS:
   self.assertEqual(transfer(ROOT/'build/craft-reference',name,'kc_fbd_'),(ROOT/'app/src/main/res/layout'/('kc_'+name+'.xml')).read_text())
 def test_every_source_menu_action_is_bound_or_explicitly_disabled(self):
  code=(ROOT/'app/src/main/java/com/bloodsky/hmi/OriginalFbdMenus.java').read_text()
  for name in MENUS:
   for node in ET.parse(ROOT/'app/src/main/res/layout'/('kc_'+name+'.xml')).iter():
    if node.get(A+'id'):self.assertIn('R.id.'+node.get(A+'id').split('/')[-1],code)
  self.assertIn('blink.setEnabled(false)',code)
 def test_canvas_uses_source_geometry_with_fractional_scaling_and_wire_endpoints(self):
  code=(ROOT/'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
  draw=(ROOT/'app/src/main/java/com/bloodsky/hmi/OriginalFbdDrawing.java').read_text()
  self.assertIn('drawing.render(canvas,w.w,w.h,caption',code)
  self.assertIn('canvas.scale(width/w,height/h)',draw)
  self.assertIn('FbdEditorModel.inputOffsetY(to,FbdEditorModel.ordinal(connection,screen.connections))',code)
 def test_context_mutations_use_synchronous_transaction_and_restore_screen_on_failure(self):
  code=(ROOT/'app/src/main/java/com/bloodsky/hmi/MainActivity.java').read_text()
  self.assertIn('FbdEditorTransaction.apply(widgets,screens.get(currentScreenIndex).connections,mutation,()->persistProjectNow(true))',code)
  self.assertIn('if(!saved) { projectExtraFields=oldProject; saveActiveScreen();',code)
  self.assertIn('if(operatorMode||screens.isEmpty())return false',code)
if __name__=='__main__':unittest.main()
