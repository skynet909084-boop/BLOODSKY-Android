package com.bloodsky.hmi;

/** Parse the complete FBD edit before the Activity changes any widget field. */
public final class FbdPropertyEdits {
    public final Integer timer, counter;
    public final Double left, right;
    public final String operator;
    private FbdPropertyEdits(Integer timer,Integer counter,Double left,String operator,Double right) {
        this.timer=timer;this.counter=counter;this.left=left;this.operator=operator;this.right=right;
    }
    public static FbdPropertyEdits parse(String timer,String counter,String left,String operator,String right) {
        return new FbdPropertyEdits(timer==null?null:integer(timer,3600000,"Время таймера"),
                counter==null?null:integer(counter,1000000,"Уставка счётчика"),
                left==null?null:number(left,"A"),operator==null?null:comparison(operator),
                right==null?null:number(right,"B"));
    }
    private static int integer(String text,int max,String label) {
        try {
            int value=Integer.parseInt(text.trim());
            if(value<1||value>max) throw new NumberFormatException();
            return value;
        } catch(RuntimeException invalid) {
            throw new IllegalArgumentException(label+": целое число от 1 до "+max);
        }
    }
    private static double number(String text,String label) {
        try {
            double value=Double.parseDouble(text.trim().replace(',','.'));
            if(Double.isNaN(value)||Double.isInfinite(value)) throw new NumberFormatException();
            return value;
        } catch(RuntimeException invalid) {
            throw new IllegalArgumentException(label+": введите конечное число");
        }
    }
    private static String comparison(String text) {
        String value=text.trim();
        if(value.equals("=")) value="==";
        if(value.equals("<>")) value="!=";
        if(value.equals(">")||value.equals("<")||value.equals(">=")||value.equals("<=")
                ||value.equals("==")||value.equals("!=")) return value;
        throw new IllegalArgumentException("Оператор: >, <, >=, <=, =, <>, == или !=");
    }
}
