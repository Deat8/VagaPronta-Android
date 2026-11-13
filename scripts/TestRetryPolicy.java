import de.deat.vagapronta.RetryPolicy;
public final class TestRetryPolicy {
    static void eq(long a,long b,String msg){if(a!=b)throw new AssertionError(msg+": esperado "+b+", obtido "+a);}
    static void str(String a,String b,String msg){if(!a.equals(b))throw new AssertionError(msg+": esperado "+b+", obtido "+a);}
    public static void main(String[] args){
        eq(RetryPolicy.clamp(-10),1,"limite inferior");
        eq(RetryPolicy.clamp(0),1,"zero");
        eq(RetryPolicy.clamp(1),1,"um minuto");
        eq(RetryPolicy.clamp(17),17,"valor interno");
        eq(RetryPolicy.clamp(30),30,"trinta minutos");
        eq(RetryPolicy.clamp(31),30,"limite superior");
        eq(RetryPolicy.milliseconds(1),60_000,"um minuto ms");
        eq(RetryPolicy.milliseconds(30),1_800_000,"trinta min ms");
        eq(RetryPolicy.deadlineAfter(1234,17),1_021_234,"agendamento");
        eq(RetryPolicy.secondsRemaining(0,60001),61,"arredondar segundos");
        eq(RetryPolicy.secondsRemaining(60100,60000),0,"vencido");
        str(RetryPolicy.countdown(65),"01:05","formatar");
        str(RetryPolicy.countdown(1800),"30:00","limite do contador");
        System.out.println("PASSOU: 12 testes reais de intervalo 1-30 min e contagem regressiva");
    }
}
