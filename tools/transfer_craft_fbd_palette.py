"""Transfer readonly Craft FBD catalogue drawings, never its executable/transport code."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from transfer_craft_component_palette import transfer, ANDROID as A

BASE_FIELDS = {
 'c': dict(S='ink', V='box', W='text', T='pin', U='stroke', b0='radius', a0='baseline', L='title', M='in1', O='in2', Q='out', R='outType', f8973e='invert', f0='path'),
 'd': dict(W='ink', c0='box', d0='text', a0='pin', b0='stroke', f0='radius', e0='baseline', N='title', O='in1', Q='in2', S='in3', U='out', V='outType', f8984e='invert', F='selected', I='selection'),
 'f': dict(f9013f='ink', G='box', f9014g='text', Q='pin', R='stroke', S='radius', E='title', f9018k='invert', W='outType', b0='path'),
 'g': dict(f9023e='ink', C='box', f9024f='text', M='pin', N='stroke', f9032n='title', T='path'),
 'b': dict(f8962e='ink', C='box', f8963f='text', M='pin', N='stroke', f8971n='title'),
}
LABELS = {'Var':'S#0.0','VarOut':'S#0.0','VarInt':'S#0','VarOutInt':'S#0',
          'VarFloat':'S#0','VarOutFloat':'S#0','VarNameIn':'var name','VarNameOut':'var name',
          'Sensor_Orientation':'ORIENTATION_X','Sunrise_GPS':'SR'}

def method(source, signature):
    start = source.index(signature) + len(signature)
    end = start; depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}'); end += 1
    return source[start:end-1]

def inventory(decoded):
    root = Path(decoded)
    entries = []
    for node in ET.parse(root/'apktool/res/layout/fragment_component_logic_bar_portrait.xml').getroot().iter():
        if not node.tag.startswith('ru.'): continue
        source = (root/'jadx/sources'/Path(node.tag.replace('.', '/')+'.java')).read_text()
        name = node.tag.split('.')[-1]
        base = re.search(r'extends ([\w.]+)', source).group(1).split('.')[-1]
        title = LABELS.get(name)
        if title is None:
            found = re.search(r'setName\("([^"]+)"\)', source) or re.search(r'this\.(?:E|f8971n) = "([^"]+)"', source)
            title = found.group(1) if found else name
        ports = [m.group(1) if (m := re.search(r'\b'+p+r'\("([^"]*)", \d\)', source)) else '' for p in 'stuv']
        entries.append((node.get(A+'id').split('/')[-1], name, base, title, ports, source))
    return entries

def layout(decoded, landscape=False):
    name = 'fragment_component_logic_bar' + ('' if landscape else '_portrait')
    root = ET.fromstring(transfer(decoded, name, 'kc_logic_', custom_views=True))
    controls = []
    for parent in root.iter():
        for node in list(parent):
            if node.get(A+'id') in ('@+id/kc_logic_switch_layout','@+id/kc_logic_close'):
                controls.append(node); parent.remove(node)
    # Keep original positions and dimensions; allow short viewports to scroll instead of clipping.
    root.set(A+'layout_height', 'match_parent' if landscape else 'wrap_content')
    if not landscape: root.set(A+'paddingBottom', '70dp')
    frame = ET.Element('FrameLayout', {A+'layout_width':'match_parent',A+'layout_height':'match_parent',A+'background':'#232323'})
    if landscape:
        frame.append(root)  # Original weighted rows require a bounded viewport height.
    else:
        scroll = ET.SubElement(frame,'ScrollView',{A+'layout_width':'match_parent',A+'layout_height':'match_parent',A+'fillViewport':'true'})
        scroll.append(root)
    for node in controls:
        for attr in list(node.attrib):
            if 'layout_alignParent' in attr: del node.attrib[attr]
        node.set(A+'layout_gravity','bottom|end' if node.get(A+'id').endswith('switch_layout') else 'bottom|start')
        frame.append(node)
    ET.indent(frame)
    return ET.tostring(frame, encoding='unicode', xml_declaration=True)

def drawing(decoded):
    bodies = []; cases = []
    for identifier, name, base, title, ports, source in inventory(decoded):
        if base == 'e':
            body = '''
        ink.setStyle(Paint.Style.FILL); ink.setColor(0xff888888); ink.setStrokeWidth(stroke);
        box.set(pin, stroke, getWidth()-pin, getHeight()-stroke); canvas.drawRect(box, ink);
        ink.setStyle(Paint.Style.STROKE); ink.setColor(Color.BLACK); canvas.drawRect(box, ink);
        int row = getHeight()/5;
        canvas.drawLine(pin,row,getWidth()-pin-stroke,row,ink);
        // Original empty macro defaults: four unnamed inputs, empty output, left column width 2*pin.
        canvas.drawLine(pin*2,row,pin*2,getHeight()-stroke,ink);
        canvas.drawText(title,getWidth()/2,row-row/2+baseline,text);
        for (int i=1;i<=4;i++) canvas.drawLine(0,row/2+row*i,pin,row/2+row*i,ink);
        canvas.drawLine(getWidth()-pin,getHeight()/2,getWidth(),getHeight()/2,ink);
'''
        else:
            draw_source = source if 'protected void onDraw(Canvas canvas) {' in source else (Path(decoded)/'jadx/sources/ru/himik/nightscream/kascada/Logic/c'/f'{base}.java').read_text()
            body = method(draw_source, 'protected void onDraw(Canvas canvas) {')
            body = body.replace('super.onDraw(canvas);', '').replace('j.d(canvas, "canvas");', '')
            body = re.sub(r'this\.(\w+)', lambda m: 'this.' + BASE_FIELDS[base].get(m.group(1), 'UNMAPPED_'+m.group(1)), body)
            body = body.replace('getName()', 'title')
            if 'UNMAPPED_' in body: raise ValueError(name+': '+body)
        body = body.rstrip()
        if body not in bodies: bodies.append(body)
        index = bodies.index(body)
        ins = ports[:3] if base == 'd' else [ports[0],ports[1],'']
        out = ports[3] if base == 'd' else ports[2]
        import json
        vals = ', '.join(json.dumps(v,ensure_ascii=False) for v in [title,*ins,out])
        cases.append(f'            case "{name}": configure({vals}, {str(base in "bfg").lower()}, {index}); break;')
    helper = method((Path(decoded)/'jadx/sources/ru/himik/nightscream/kascada/Logic/VarFloat.java').read_text(), 'public Path u(RectF rectF, float f2, float f3, float f4, float f5) {')
    java = '''package com.bloodsky.hmi;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

/** Generated source drawings; catalogue defaults and editor rendering share geometry. */
public final class OriginalFbdDrawing extends View {
    private final Paint ink = new Paint(), text = new Paint(), selection = new Paint();
    private final RectF box = new RectF();
    private final Path path = new Path();
    private int pin, stroke, radius;
    private float baseline;
    private String title="", in1="", in2="", in3="", out="";
    private boolean invert=false;
    private final boolean selected=false;
    private final int outType=1;
    private boolean variable;
    private int drawing;
    public OriginalFbdDrawing(Context context, AttributeSet attrs) {
        super(context,attrs);
        initialize(String.valueOf(getTag()));
    }
    public OriginalFbdDrawing(Context context, String kind) {
        super(context); initialize(kind);
    }
    private void initialize(String kind) {
        float density=getResources().getDisplayMetrics().density;
        pin=(int)(10*density); stroke=(int)density; radius=(int)(3*density);
        switch (kind) {
''' + '\n'.join(cases) + '''
            default: throw new IllegalArgumentException("Unknown FBD preview: "+kind);
        }
    }
    private void configure(String label,String first,String second,String third,String output,boolean var,int kind) {
        title=label; in1=first; in2=second; in3=third; out=output; variable=var; drawing=kind;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        super.onMeasure(widthSpec,heightSpec);
        prepareText(getMeasuredHeight());
        if (variable) {
            Rect bounds=new Rect(); text.getTextBounds(title,0,title.length(),bounds);
            setMeasuredDimension(bounds.width()+pin*3,getMeasuredHeight());
        }
    }
    private void prepareText(int height) {
        text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.WHITE);
        text.setTextSize(variable ? height-pin/2 : 10*getResources().getDisplayMetrics().density);
        text.setAntiAlias(variable);
        ink.setAntiAlias(variable); ink.setStrokeWidth(stroke);
        baseline=((text.descent()-text.ascent())/2)-text.descent();
    }
    /** Draw into an existing canvas while retaining fractional widget bounds. */
    public void render(Canvas canvas,float width,float height,String caption,boolean inverted,String timeUnit) {
        int w=Math.max(1,Math.round(width)), h=Math.max(1,Math.round(height));
        layout(0,0,w,h); prepareText(h);
        String saved=title, savedInput=in2; title=caption; invert=inverted;
        if("MS".equals(in2))in2=timeUnit;
        int checkpoint=canvas.save();
        canvas.scale(width/w,height/h); canvas.clipRect(0,0,w,h);
        onDraw(canvas); canvas.restoreToCount(checkpoint);
        title=saved; in2=savedInput; invert=false;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch(drawing) {
''' + '\n'.join(f'            case {i}: draw{i}(canvas); break;' for i in range(len(bodies))) + '''
        }
    }
''' + '\n'.join(f'    private void draw{i}(Canvas canvas) {{{body}\n    }}\n' for i,body in enumerate(bodies)) + '    private Path u(RectF rectF,float f2,float f3,float f4,float f5) {'+helper+'\n    }\n}\n'

    return '\n'.join(line.rstrip() for line in java.splitlines()) + '\n'

if __name__ == '__main__':
    root=Path(__file__).resolve().parents[1]; decoded=root/'build/craft-reference'
    (root/'app/src/main/java/com/bloodsky/hmi/OriginalFbdDrawing.java').write_text(drawing(decoded))
    for name in ['var_float','var_out_float']:
        dest=root/'app/src/main/res/drawable'/('kc_'+name+'.xml')
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes((decoded/'apktool/res/drawable'/(name+'.xml')).read_bytes())
    for qualifier,land in [('layout',False),('layout-land',True)]:
        dest=root/'app/src/main/res'/qualifier/'kc_fragment_component_logic_bar.xml'
        dest.parent.mkdir(parents=True,exist_ok=True); dest.write_text(layout(decoded,land))
