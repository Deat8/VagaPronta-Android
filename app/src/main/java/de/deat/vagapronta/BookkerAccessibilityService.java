package de.deat.vagapronta;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Controla APENAS a interface acessível do Bookker.
 * Nunca lê ou copia tokens/credenciais, não executa cliques por coordenadas e
 * não repete uma tentativa de reserva cujo resultado seja desconhecido.
 *
 * A interface real do Bookker ainda precisa de teste presencial: caso os nós
 * não sejam expostos pela acessibilidade, o serviço para de forma segura.
 */
public final class BookkerAccessibilityService extends AccessibilityService {
    private static final String BOOKKER = "com.bookkercorporate.b2b";
    private static final long ACTION_TIMEOUT = 25_000;
    private static final long INPUT_SETTLE = 800;
    private enum Phase { IDLE, WAIT_FORM, SCANNING, WAIT_UI, WAIT_BOOK, RETRY, HALTED }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> attempted = new HashSet<>();
    private Prefs prefs;
    private Phase phase = Phase.IDLE;
    private long nextRunAt = 0;
    private long actionDeadline = 0;
    private long actionCooldown = 0;
    private int actionsThisCycle = 0;
    private boolean queriedCurrentFloor = false;
    private boolean previousActive = false;
    private WindowManager wm;
    private View floating;
    private TextView floatingText;

    private final Runnable ticker = new Runnable() {
        @Override public void run(){
            try {tick();} catch (RuntimeException ex){halt("Falha de leitura da tela. Reinicie a busca.");}
            handler.postDelayed(this,1000);
        }
    };

    @Override protected void onServiceConnected(){
        super.onServiceConnected();prefs=new Prefs(this);
        if(prefs.active()){
            // Nunca retomar automaticamente após reinício do processo: resultado anterior pode ser incerto.
            prefs.active(false);prefs.message("Serviço reiniciado pelo Android; inicie uma nova busca manualmente.");
        }
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        handler.removeCallbacks(ticker);handler.post(ticker);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(prefs==null || !prefs.active())return;
        if(event==null || event.getPackageName()==null || !BOOKKER.contentEquals(event.getPackageName()))return;
        handler.removeCallbacks(processPending);
        handler.postDelayed(processPending,200);
    }
    private final Runnable processPending=()->{
        if(prefs!=null && prefs.active()) {
            try {process();}catch(RuntimeException ex){halt("Erro ao interpretar controles do Bookker.");}
        }
    };
    @Override public void onInterrupt(){if(prefs!=null && prefs.active())halt("Serviço de acessibilidade interrompido.");}
    @Override public void onDestroy(){handler.removeCallbacks(ticker);handler.removeCallbacks(processPending);
        if(prefs!=null && prefs.active()){prefs.active(false);prefs.message("Serviço interrompido pelo Android. Inicie novamente.");}
        hideOverlay();super.onDestroy();}

    private void tick(){
        boolean active=prefs.active();
        if(!active){previousActive=false;phase=Phase.IDLE;hideOverlay();return;}
        if(!previousActive){previousActive=true;phase=Phase.WAIT_FORM;attempted.clear();actionsThisCycle=0;queriedCurrentFloor=false;nextRunAt=0;status("Aguardando a tela de reserva no Bookker.");}
        if(!bookkerVisible()){
            hideOverlay();
            return; // Nunca operar ou exibir sobre login Microsoft ou outros aplicativos.
        }
        showOverlay();
        long now=SystemClock.elapsedRealtime();
        if(phase==Phase.RETRY){
            if(now>=nextRunAt){
                if(!bookkerVisible()){floatingText.setText("Bookker fora da tela; abra-o para continuar.");return;}
                phase=Phase.WAIT_FORM;
                actionCooldown=now;
                attempted.clear();actionsThisCycle=0;queriedCurrentFloor=false;
                status("Intervalo terminado. Verificando próxima rodada.");
            }else{
                long remaining=RetryPolicy.secondsRemaining(now,nextRunAt);
                floatingText.setText("Próxima rodada: "+RetryPolicy.countdown(remaining)+" · "+prefs.minutes()+" min");
                return;
            }
        }
        if((phase==Phase.WAIT_UI || phase==Phase.WAIT_BOOK) && now>actionDeadline){
            halt("Resultado não confirmado. Confira Reservas no Bookker antes de reiniciar.");return;
        }
        if(phase==Phase.WAIT_FORM || phase==Phase.SCANNING || phase==Phase.WAIT_UI || phase==Phase.WAIT_BOOK){
            floatingText.setText(prefs.message());
            if(now>=actionCooldown)process();
        }
    }
    private boolean bookkerVisible(){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        return root!=null && root.getPackageName()!=null && BOOKKER.contentEquals(root.getPackageName());
    }
    private void process(){
        if(!prefs.active() || phase==Phase.IDLE || phase==Phase.HALTED || phase==Phase.RETRY)return;
        long now=SystemClock.elapsedRealtime();if(now<actionCooldown)return;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null||root.getPackageName()==null||!BOOKKER.contentEquals(root.getPackageName()))return;
        Snapshot s=new Snapshot(root);
        if(s.hasAny("reserva realizada com sucesso","reserva criada com sucesso","reserva efetuada com sucesso","vaga reservada","reserva confirmada com sucesso","estacionamento reservado com sucesso")){
            finish("Vaga reservada no Bookker. Confira em Reservas.");return;
        }
        if(s.hasAny("ja possui reserva","ja existe uma reserva","voce ja tem uma reserva","reserva existente")){
            finish("Já existe uma reserva. Busca encerrada.");return;
        }
        if(s.hasAny("sessao expirada","faca login","entrar com microsoft","efetue login")){
            halt("Sessão expirada. Faça login novamente no Bookker.");return;
        }
        if(phase==Phase.WAIT_BOOK){
            if(s.hasAny("confirmar reserva","finalizar reserva") && now>=actionCooldown){
                if(s.clickAction("confirmar reserva","finalizar reserva")){
                    actionCooldown=now+INPUT_SETTLE;actionDeadline=now+ACTION_TIMEOUT;
                    status("Confirmação enviada. Aguardando resposta do Bookker.");
                }
            }
            return; // Uma reserva incerta jamais é repetida sem comprovação.
        }
        boolean noVacancy=s.hasAny("sem vagas","nenhuma vaga","nao ha vagas","nao existem vagas","lotado","esgotado","sem disponibilidade","nenhuma disponibilidade");
        if(phase==Phase.WAIT_UI){
            if(noVacancy){phase=Phase.SCANNING;actionCooldown=now+450;status("Sem vagas nesta opção. Buscando outra.");return;}
            if(s.hasAny("vaga disponivel","vagas disponiveis","disponibilidade confirmada","vaga livre")){
                phase=Phase.SCANNING;
            }else{
                if(!queriedCurrentFloor && now>=actionCooldown &&
                    s.clickAction("buscar vagas","consultar vagas","ver disponibilidade","buscar estacionamentos")){
                    queriedCurrentFloor=true;
                    actionsThisCycle++;
                    actionCooldown=now+INPUT_SETTLE;
                    actionDeadline=now+ACTION_TIMEOUT;
                    status("Consultando disponibilidade na opção selecionada.");
                }
                return;
            }
        }
        if(phase==Phase.WAIT_FORM){
            if(!s.hasAny("estacionamento","reservar estacionamento","reserva de estacionamento","andar","garagem","vagas")){
                status("Abra no Bookker a reserva de estacionamento, com data, veículo e turno escolhidos.");return;
            }
            prefs.increaseCycles();attempted.clear();actionsThisCycle=0;queriedCurrentFloor=false;
            phase=Phase.SCANNING;status("Procurando vagas · rodada "+prefs.cycles());
        }
        if(s.hasAny("carregando","aguarde um momento"))return;
        if(s.hasAny("vaga disponivel","vagas disponiveis","vaga livre") &&
           s.clickAction("reservar vaga","reservar estacionamento","reservar")){
            phase=Phase.WAIT_BOOK;actionCooldown=now+INPUT_SETTLE;actionDeadline=now+ACTION_TIMEOUT;
            status("Pedido de reserva enviado. Aguardando confirmação; não haverá segunda tentativa cega.");return;
        }
        if(actionsThisCycle>=16){halt("Limite de opções alcançado; confira a tela do Bookker.");return;}
        if(noVacancy || phase==Phase.SCANNING){
            Choice next=s.findNewChoice(attempted);
            if(next!=null){
                attempted.add(next.id);
                if(next.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                    actionsThisCycle++;queriedCurrentFloor=false;
                    actionCooldown=now+INPUT_SETTLE;
                    actionDeadline=now+ACTION_TIMEOUT;
                    phase=Phase.WAIT_UI;
                    status("Consultando "+next.id+" ("+attempted.size()+" opções verificadas).");return;
                }
            }
            if(noVacancy){scheduleRetry();return;}
        }
        if(actionsThisCycle==0 && s.clickAction("buscar vagas","consultar vagas","ver disponibilidade","buscar estacionamentos")){
            actionsThisCycle++;queriedCurrentFloor=true;phase=Phase.WAIT_UI;actionCooldown=now+INPUT_SETTLE;
            actionDeadline=now+ACTION_TIMEOUT;status("Consultando vagas no Bookker.");return;
        }
        if(actionsThisCycle==0){
            // Conservador: não inferir nem tocar controles desconhecidos.
            status("Nenhum controle seguro encontrado. Deixe o Bookker na tela de vagas.");
        }else if(phase==Phase.SCANNING){
            halt("Resposta inconclusiva. Confira as reservas antes de tentar novamente.");
        }
    }
    private void scheduleRetry(){
        phase=Phase.RETRY;
        nextRunAt=RetryPolicy.deadlineAfter(SystemClock.elapsedRealtime(),prefs.minutes());
        status("Todos os estacionamentos testados sem vagas. Repetir em "+prefs.minutes()+" min.");
    }
    private void status(String value){prefs.message(value);if(floatingText!=null)floatingText.setText(value);}
    private void finish(String msg){prefs.active(false);prefs.message(msg);phase=Phase.HALTED;
        if(floatingText!=null)floatingText.setText(msg);handler.postDelayed(this::hideOverlay,3500);}
    private void halt(String msg){prefs.active(false);prefs.message(msg);phase=Phase.HALTED;
        if(floatingText!=null)floatingText.setText(msg);handler.postDelayed(this::hideOverlay,4000);}

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void showOverlay(){
        if(floating!=null || wm==null)return;
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(11),dp(5),dp(10),dp(5));bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(15,33,51));
        floatingText=new TextView(this);floatingText.setTextColor(Color.WHITE);floatingText.setTextSize(12);
        floatingText.setTypeface(Typeface.DEFAULT,Typeface.BOLD);floatingText.setText("Busca ativa");
        bar.addView(floatingText,new LinearLayout.LayoutParams(0,dp(52),1));
        Button stop=new Button(this);stop.setText("PARAR");stop.setTextSize(11);stop.setAllCaps(false);
        bar.addView(stop,new LinearLayout.LayoutParams(dp(78),dp(52)));
        stop.setOnClickListener(v->{prefs.active(false);prefs.message("Parado pelo usuário.");hideOverlay();});
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP;
        try{wm.addView(bar,lp);floating=bar;}catch(RuntimeException ex){floating=null;floatingText=null;}
    }
    private void hideOverlay(){
        if(floating!=null&&wm!=null){try{wm.removeView(floating);}catch(RuntimeException ignored){}}
        floating=null;floatingText=null;
    }
    private static String norm(String input){
        if(input==null)return "";
        return Normalizer.normalize(input,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
            .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");
    }
    private static final class Choice {
        final String id;final AccessibilityNodeInfo node;
        Choice(String id,AccessibilityNodeInfo node){this.id=id;this.node=node;}
    }
    private static final class Snapshot {
        private final List<AccessibilityNodeInfo> nodes=new ArrayList<>();
        private final List<String> texts=new ArrayList<>();
        private final String all;
        Snapshot(AccessibilityNodeInfo root){walk(root,0);StringBuilder b=new StringBuilder();
            for(String t:texts)b.append(' ').append(t);all=b.toString();}
        private void walk(AccessibilityNodeInfo n,int depth){
            if(n==null||depth>35||nodes.size()>900)return;
            nodes.add(n);
            String t=n.isPassword()?"":norm(n.getText()==null?null:n.getText().toString());
            String d=n.isPassword()?"":norm(n.getContentDescription()==null?null:n.getContentDescription().toString());
            texts.add(t.isEmpty()?d:t+" "+d);
            for(int i=0;i<n.getChildCount();i++)walk(n.getChild(i),depth+1);
        }
        boolean hasAny(String... values){for(String v:values)if(all.contains(norm(v)))return true;return false;}
        boolean clickAction(String... labels){
            for(String q:labels){String needle=norm(q);
                for(int i=0;i<nodes.size();i++){
                    String t=texts.get(i);
                    if(t.isEmpty()||!t.contains(needle))continue;
                    AccessibilityNodeInfo n=clickableAncestor(nodes.get(i));
                    if(n!=null && n.isEnabled() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
                }
            }
            return false;
        }
        Choice findNewChoice(Set<String> attempted){
            for(int i=0;i<nodes.size();i++){
                String text=texts.get(i);
                if(text.length()<3||text.length()>85)continue;
                if(!text.matches(".*\\b(estacionamento|garagem|andar|piso|floor)\\b.*"))continue;
                if(text.matches(".*(reservar|cancelar|buscar|selecione|selecao|disponibilidade).*"))continue;
                AccessibilityNodeInfo click=clickableAncestor(nodes.get(i));
                if(click!=null && click.isEnabled() && !attempted.contains(text))return new Choice(text,click);
            }
            return null;
        }
        private AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo n){
            for(int d=0;n!=null&&d<5;d++,n=n.getParent()){
                if(n.isClickable())return n;
            }
            return null;
        }
    }
}
