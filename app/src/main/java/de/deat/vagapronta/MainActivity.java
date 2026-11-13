package de.deat.vagapronta;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.accessibility.AccessibilityManager;
import java.util.List;

public final class MainActivity extends Activity {
    private Prefs prefs;
    private TextView minutesLabel, statusLabel;
    private SeekBar slider;
    private final int bg = Color.rgb(15,26,40);
    private final int accent = Color.rgb(47,192,190);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); prefs = new Prefs(this); getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        LinearLayout root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,24,28,32);root.setBackgroundColor(bg);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        title(root,"VAGA PRONTA",26,Color.WHITE,true);
        title(root,"Busca pelo Bookker original · v0.3",14,Color.rgb(142,174,193),false);
        title(root,"INTERVALO ENTRE TENTATIVAS",12,accent,true);
        minutesLabel=title(root,"",24,Color.WHITE,true);
        slider=new SeekBar(this);slider.setMax(29);slider.setProgress(prefs.minutes()-1);root.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int progress,boolean fromUser){
                int m=progress+1;minutesLabel.setText(m+(m==1?" minuto":" minutos"));
                if(fromUser){prefs.minutes(m);}
            }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        LinearLayout adjust=new LinearLayout(this);adjust.setOrientation(LinearLayout.HORIZONTAL);root.addView(adjust);
        Button less=button(adjust,"− 1 min",false, ()->slider.setProgress(Math.max(0,slider.getProgress()-1)));
        Button more=button(adjust,"+ 1 min",false, ()->slider.setProgress(Math.min(29,slider.getProgress()+1)));
        less.setLayoutParams(new LinearLayout.LayoutParams(0,dp(46),1));more.setLayoutParams(new LinearLayout.LayoutParams(0,dp(46),1));
        title(root,"A próxima tentativa é feita somente depois que uma rodada termina sem vagas. A contagem aparece no painel flutuante.",13,Color.rgb(181,202,216),false);
        title(root,"CONTROLES",12,accent,true);
        button(root,"1 · Ativar acessibilidade",false, ()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(root,"2 · Abrir Bookker",false, this::openBookker);
        button(root,"3 · Iniciar busca automática",true,()->{
            if(!enabled()){statusLabel.setText("Ative o serviço Vaga Pronta na acessibilidade antes de iniciar.");
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
            prefs.minutes(slider.getProgress()+1);prefs.resetCycles();prefs.active(true);
            prefs.message("Aguardando a tela de reserva do Bookker.");openBookker();
        });
        button(root,"Parar tentativas",false,()->{prefs.active(false);prefs.message("Parado pelo usuário.");
            statusLabel.setText("Parado pelo usuário.");});
        title(root,"STATUS",12,accent,true);
        statusLabel=title(root,"",15,Color.WHITE,false);
        title(root,"IMPORTANTE",12,accent,true);
        title(root,"Antes de iniciar, entre no Bookker e deixe selecionados veículo, prédio, data e turno na tela de reserva. Mantenha o celular desbloqueado e o Bookker visível. O painel flutuante permite parar a qualquer momento.",13,Color.rgb(181,202,216),false);
        title(root,"O aplicativo observa apenas o Bookker. Ele não copia a sessão Microsoft, nem acessa sua senha. Se não conseguir interpretar a tela com segurança, para a busca em vez de clicar às cegas.",12,Color.rgb(153,175,188),false);
        refresh();
    }
    @Override public void onResume(){super.onResume();if(prefs!=null && statusLabel!=null)refresh();}
    private void refresh(){minutesLabel.setText(prefs.minutes()+(prefs.minutes()==1?" minuto":" minutos"));
        slider.setProgress(prefs.minutes()-1);
        statusLabel.setText((enabled()?"Acessibilidade ligada. ":"Acessibilidade desligada. ")+prefs.message()+"\nRodadas: "+prefs.cycles());}
    private boolean enabled(){
        AccessibilityManager m=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        if(m==null)return false;
        List<AccessibilityServiceInfo> services=m.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for(AccessibilityServiceInfo s:services){if(s.getId()!=null && s.getId().contains(getPackageName()+"/"))return true;}
        return false;
    }
    private void openBookker(){Intent i=getPackageManager().getLaunchIntentForPackage("com.bookkercorporate.b2b");
        if(i==null){if(statusLabel!=null)statusLabel.setText("Bookker não encontrado no mesmo perfil Android.");return;}
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView title(LinearLayout parent,String text,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setPadding(0,dp(10),0,dp(8));parent.addView(v);return v;
    }
    private Button button(LinearLayout parent,String text,boolean primary,Runnable click){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(primary?Color.BLACK:Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?accent:Color.rgb(44,64,83)));
        parent.addView(b);b.setOnClickListener(v->click.run());return b;
    }
}
