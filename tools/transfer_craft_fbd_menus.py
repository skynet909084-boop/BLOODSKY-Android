"""Resolve original native FBD context and timer menus without altering their hierarchy."""
from pathlib import Path
from transfer_craft_component_palette import transfer
MENUS=['popup_logic_fb_and_or','popup_logic_fb_timer','popup_logic_fb_pow',
       'popup_logic_fb_timer_edit_type','popup_logic_fb_timer_edit_type_time']
if __name__=='__main__':
 root=Path(__file__).resolve().parents[1]
 for name in MENUS:
  (root/'app/src/main/res/layout'/('kc_'+name+'.xml')).write_text(transfer(root/'build/craft-reference',name,'kc_fbd_'))
