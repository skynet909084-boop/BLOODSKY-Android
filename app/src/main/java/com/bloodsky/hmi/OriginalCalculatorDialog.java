package com.bloodsky.hmi;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


/* JADX INFO: loaded from: classes.dex */
/** Adapted from Craft 1.0.7 p.java; see docs/ORIGINAL_CALCULATOR_TRANSFER.md. */
public class OriginalCalculatorDialog extends DialogFragment implements View.OnClickListener {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Dialog_NoActionBar);
    }

    Button C;
    Button D;
    Button E;
    Button F;
    Button G;
    float H;
    boolean I = false;
    String J = "";
    boolean K = false;
    TextView a;
    Button b;
    Button c;

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    Button f9236d;

    /* JADX INFO: renamed from: e, reason: collision with root package name */
    Button f9237e;

    /* JADX INFO: renamed from: f, reason: collision with root package name */
    Button f9238f;

    /* JADX INFO: renamed from: g, reason: collision with root package name */
    Button f9239g;

    /* JADX INFO: renamed from: h, reason: collision with root package name */
    Button f9240h;

    /* JADX INFO: renamed from: i, reason: collision with root package name */
    Button f9241i;

    /* JADX INFO: renamed from: j, reason: collision with root package name */
    Button f9242j;

    /* JADX INFO: renamed from: k, reason: collision with root package name */
    Button f9243k;

    /* JADX INFO: renamed from: l, reason: collision with root package name */
    Button f9244l;

    /* JADX INFO: renamed from: m, reason: collision with root package name */
    Button f9245m;

    /* JADX INFO: renamed from: n, reason: collision with root package name */
    Button f9246n;
    Button o;

    void a(String str) {
        TextView textView;
        StringBuilder sb;
        if (this.K) {
            d();
            this.K = false;
        }
        if (this.I) {
            this.a.setText("");
            this.I = false;
            textView = this.a;
            sb = new StringBuilder();
        } else {
            textView = this.a;
            sb = new StringBuilder();
        }
        sb.append(this.a.getText().toString());
        sb.append(str);
        textView.setText(sb.toString());
    }

    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    void b() {
        String str = this.J;
        str.hashCode();
        byte b = -1;
        switch (str.hashCode()) {
            case 99473:
                if (str.equals("div")) {
                    b = 0;
                }
                break;
            case 108484:
                if (str.equals("mul")) {
                    b = 1;
                }
                break;
            case 3444122:
                if (str.equals("plus")) {
                    b = 2;
                }
                break;
            case 103901296:
                if (str.equals("minus")) {
                    b = 3;
                }
                break;
        }
        try {
            switch (b) {
                case 0:
                    if (!Float.isInfinite(this.H / Float.parseFloat(this.a.getText().toString()))) {
                        this.a.setText((this.H / Float.parseFloat(this.a.getText().toString())) + "");
                    } else {
                        Toast.makeText(getActivity(), "Бесконечность", 1).show();
                        d();
                    }
                    break;
                case 1:
                    if (!Float.isInfinite(this.H * Float.parseFloat(this.a.getText().toString()))) {
                        this.a.setText((this.H * Float.parseFloat(this.a.getText().toString())) + "");
                    } else {
                        Toast.makeText(getActivity(), "Бесконечность", 1).show();
                        d();
                    }
                    break;
                case 2:
                    if (!Float.isInfinite(this.H + Float.parseFloat(this.a.getText().toString()))) {
                        this.a.setText((this.H + Float.parseFloat(this.a.getText().toString())) + "");
                    } else {
                        Toast.makeText(getActivity(), "Бесконечность", 1).show();
                        d();
                    }
                    break;
                case 3:
                    if (!Float.isInfinite(this.H - Float.parseFloat(this.a.getText().toString()))) {
                        this.a.setText((this.H - Float.parseFloat(this.a.getText().toString())) + "");
                    } else {
                        Toast.makeText(getActivity(), "Бесконечность", 1).show();
                        d();
                    }
                    break;
                default:
                    return;
            }
            this.J = "";
        } catch (Exception unused) {
            Toast.makeText(getActivity(), "Ошибка", 1).show();
            d();
        }
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code duplicated, block: B:4:0x0017  */
    void c(String str) {
        byte b = 0;
        this.K = false;
        str.hashCode();
        switch (str.hashCode()) {
            case 3244:
                if (!str.equals("eq")) {
                    b = -1;
                }
                break;
            case 99473:
                if (!str.equals("div")) {
                    b = -1;
                } else {
                    b = 1;
                }
                break;
            case 108484:
                if (!str.equals("mul")) {
                    b = -1;
                } else {
                    b = 2;
                }
                break;
            case 3444122:
                if (!str.equals("plus")) {
                    b = -1;
                } else {
                    b = 3;
                }
                break;
            case 103901296:
                if (!str.equals("minus")) {
                    b = -1;
                } else {
                    b = 4;
                }
                break;
            default:
                b = -1;
                break;
        }
        switch (b) {
            case 0:
                this.K = true;
                b();
                break;
            case 1:
                if (!this.I) {
                    if (!this.J.equals("div")) {
                        b();
                    }
                    try {
                        this.H = Float.parseFloat(this.a.getText().toString());
                    } catch (Exception unused) {
                        Toast.makeText(getActivity(), "Ошибка", 1).show();
                        d();
                    }
                    this.I = true;
                }
                this.J = "div";
                break;
            case 2:
                if (!this.I) {
                    if (!this.J.equals("mul")) {
                        b();
                    }
                    try {
                        this.H = Float.parseFloat(this.a.getText().toString());
                    } catch (Exception unused2) {
                        Toast.makeText(getActivity(), "Ошибка", 1).show();
                        d();
                    }
                    this.I = true;
                }
                this.J = "mul";
                break;
            case 3:
                if (!this.I) {
                    if (!this.J.equals("plus")) {
                        b();
                    }
                    try {
                        this.H = Float.parseFloat(this.a.getText().toString());
                    } catch (Exception unused3) {
                        Toast.makeText(getActivity(), "Ошибка", 1).show();
                        d();
                    }
                    this.I = true;
                }
                this.J = "plus";
                break;
            case 4:
                if (!this.I) {
                    if (!this.J.equals("minus")) {
                        b();
                    }
                    try {
                        this.H = Float.parseFloat(this.a.getText().toString());
                    } catch (Exception unused4) {
                        Toast.makeText(getActivity(), "Ошибка", 1).show();
                        d();
                    }
                    this.I = true;
                }
                this.J = "minus";
                break;
        }
    }

    void d() {
        this.a.setText("");
        this.H = 0.0f;
        this.I = false;
        this.J = "";
    }

    @Override public void onClick(View view) {
        int id = view.getId();
        int[] digits = {R.id.kc_calc_b0,R.id.kc_calc_b1,R.id.kc_calc_b2,R.id.kc_calc_b3,R.id.kc_calc_b4,
                R.id.kc_calc_b5,R.id.kc_calc_b6,R.id.kc_calc_b7,R.id.kc_calc_b8,R.id.kc_calc_b9};
        for (int i = 0; i < digits.length; i++) if (id == digits[i]) { a(Integer.toString(i)); return; }
        if (id == R.id.kc_calc_bClear) { d(); return; }
        if (id == R.id.kc_calc_bDelete) {
            String text = a.getText().toString();
            if (!text.isEmpty()) a.setText(text.substring(0,text.length()-1));
            return;
        }
        if (id == R.id.kc_calc_bExit) { dismiss(); return; }
        if (id == R.id.kc_calc_bPoint) { if (!a.getText().toString().contains(".")) a("."); return; }
        if (id == R.id.kc_calc_bDiv) c("div");
        else if (id == R.id.kc_calc_bMul) c("mul");
        else if (id == R.id.kc_calc_bPlus) c("plus");
        else if (id == R.id.kc_calc_bMinus) c("minus");
        else if (id == R.id.kc_calc_bEq) c("eq");
    }

    @Override // android.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        getDialog().getWindow().requestFeature(1);
        getDialog().setCanceledOnTouchOutside(true);
        View viewInflate = layoutInflater.inflate(R.layout.kc_calculator, viewGroup, false);
        this.a = (TextView) viewInflate.findViewById(R.id.kc_calc_textOut);
        Button button = (Button) viewInflate.findViewById(R.id.kc_calc_b1);
        this.b = button;
        button.setOnClickListener(this);
        Button button2 = (Button) viewInflate.findViewById(R.id.kc_calc_b2);
        this.c = button2;
        button2.setOnClickListener(this);
        Button button3 = (Button) viewInflate.findViewById(R.id.kc_calc_b3);
        this.f9236d = button3;
        button3.setOnClickListener(this);
        Button button4 = (Button) viewInflate.findViewById(R.id.kc_calc_b4);
        this.f9237e = button4;
        button4.setOnClickListener(this);
        Button button5 = (Button) viewInflate.findViewById(R.id.kc_calc_b5);
        this.f9238f = button5;
        button5.setOnClickListener(this);
        Button button6 = (Button) viewInflate.findViewById(R.id.kc_calc_b6);
        this.f9239g = button6;
        button6.setOnClickListener(this);
        Button button7 = (Button) viewInflate.findViewById(R.id.kc_calc_b7);
        this.f9240h = button7;
        button7.setOnClickListener(this);
        Button button8 = (Button) viewInflate.findViewById(R.id.kc_calc_b8);
        this.f9241i = button8;
        button8.setOnClickListener(this);
        Button button9 = (Button) viewInflate.findViewById(R.id.kc_calc_b9);
        this.f9242j = button9;
        button9.setOnClickListener(this);
        Button button10 = (Button) viewInflate.findViewById(R.id.kc_calc_b0);
        this.f9243k = button10;
        button10.setOnClickListener(this);
        Button button11 = (Button) viewInflate.findViewById(R.id.kc_calc_bDiv);
        this.f9244l = button11;
        button11.setOnClickListener(this);
        Button button12 = (Button) viewInflate.findViewById(R.id.kc_calc_bMul);
        this.f9245m = button12;
        button12.setOnClickListener(this);
        Button button13 = (Button) viewInflate.findViewById(R.id.kc_calc_bPlus);
        this.f9246n = button13;
        button13.setOnClickListener(this);
        Button button14 = (Button) viewInflate.findViewById(R.id.kc_calc_bMinus);
        this.o = button14;
        button14.setOnClickListener(this);
        Button button15 = (Button) viewInflate.findViewById(R.id.kc_calc_bEq);
        this.C = button15;
        button15.setOnClickListener(this);
        Button button16 = (Button) viewInflate.findViewById(R.id.kc_calc_bPoint);
        this.D = button16;
        button16.setOnClickListener(this);
        Button button17 = (Button) viewInflate.findViewById(R.id.kc_calc_bExit);
        this.F = button17;
        button17.setOnClickListener(this);
        Button button18 = (Button) viewInflate.findViewById(R.id.kc_calc_bClear);
        this.E = button18;
        button18.setOnClickListener(this);
        Button button19 = (Button) viewInflate.findViewById(R.id.kc_calc_bDelete);
        this.G = button19;
        button19.setOnClickListener(this);
        return viewInflate;
    }
}
