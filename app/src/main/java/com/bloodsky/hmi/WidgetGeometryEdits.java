package com.bloodsky.hmi;

/** Validate all four property fields before mutating a widget; bounds match project import. */
public final class WidgetGeometryEdits {
    public final float x, y, width, height;
    private WidgetGeometryEdits(float x, float y, float width, float height) {
        this.x=x; this.y=y; this.width=width; this.height=height;
    }
    public static WidgetGeometryEdits parse(String x, String y, String width, String height) {
        return new WidgetGeometryEdits(value(x,-100000,"X"), value(y,-100000,"Y"),
                value(width,1,"Ширина"), value(height,1,"Высота"));
    }
    private static float value(String text, double minimum, String label) {
        try {
            double number=Double.parseDouble(text.trim().replace(',', '.'));
            if (Double.isNaN(number) || Double.isInfinite(number) || number<minimum || number>100000)
                throw new NumberFormatException();
            return (float)number;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(label+": допустимо от "+(int)minimum+" до 100000 пикселей");
        }
    }
}
