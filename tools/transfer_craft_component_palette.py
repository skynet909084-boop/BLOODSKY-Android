"""Resolve the original component palette into independently compilable Android XML."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', ANDROID[1:-1])

def transfer(decoded, layout_name="fragment_component_bar", prefix="kc_picker_", subtree_id=None):
    original = Path(decoded) / 'apktool/res'
    values = {}
    for filename in ['colors.xml', 'dimens.xml', 'strings.xml']:
        for item in ET.parse(original / 'values' / filename).getroot():
            values[(item.tag, item.get('name'))] = item.text or ''
    for item in ET.parse(original / 'values-ru/strings.xml').getroot():
        values[('string', item.get('name'))] = item.text or ''
    def literal(kind, name):
        value = values[(kind, name)]
        if value.startswith('@') and not value.startswith('@android:'):
            return literal(*value[1:].split('/'))
        return value
    root = ET.parse(original / 'layout' / (layout_name + '.xml')).getroot()
    if subtree_id is not None:
        root = next(node for node in root.iter() if node.get(ANDROID + 'id') == '@id/' + subtree_id)
    for element in root.iter():
        # These are static catalogue thumbnails, never replacement runtime components.
        if element.tag.startswith('ru.himik.'):
            element.tag = 'ImageView' if element.tag.endswith('CustomSound') else 'TextView'
        for attr, value in list(element.attrib.items()):
            if value.startswith(('@id/', '@+id/')):
                value = '@+id/' + prefix + value.split('/')[-1]
            elif value.startswith('@drawable/'):
                value = '@drawable/kc_' + value.split('/')[-1]
            elif value.startswith(('@color/', '@dimen/', '@string/')):
                value = literal(*value[1:].split('/'))
            element.set(attr, value)
    ET.indent(root)
    return ET.tostring(root, encoding='unicode', xml_declaration=True)

def selection_actions(decoded):
    root = ET.fromstring(transfer(decoded, 'activity_work_portrait', 'kc_action_', 'layoutSelectAction'))
    # Original handlers live on mbCanvas. Bind the same actions explicitly in BLOODSKY.
    for node in root.iter():
        handler = node.attrib.pop(ANDROID + 'onClick', None)
        if handler is not None:
            node.set(ANDROID + 'id', '@+id/kc_action_' + handler)
    ET.indent(root)
    return ET.tostring(root, encoding='unicode', xml_declaration=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--decoded', type=Path, default=Path('build/craft-reference'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(transfer(args.decoded), encoding='utf-8')
