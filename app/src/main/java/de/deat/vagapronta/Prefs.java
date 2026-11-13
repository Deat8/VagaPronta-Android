package de.deat.vagapronta;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String NAME="vagapronta_03";
    private final SharedPreferences p;
    Prefs(Context ctx){p=ctx.getSharedPreferences(NAME,Context.MODE_PRIVATE);}
    int minutes(){return RetryPolicy.clamp(p.getInt("minutes",15));}
    void minutes(int value){p.edit().putInt("minutes",RetryPolicy.clamp(value)).apply();}
    boolean active(){return p.getBoolean("active",false);}
    void active(boolean value){p.edit().putBoolean("active",value).apply();}
    String message(){return p.getString("message","Busca ainda não iniciada.");}
    void message(String msg){p.edit().putString("message",msg).apply();}
    int cycles(){return p.getInt("cycles",0);}
    int increaseCycles(){int n=cycles()+1;p.edit().putInt("cycles",n).apply();return n;}
    void resetCycles(){p.edit().putInt("cycles",0).apply();}
}
