package REST.beans;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class Statistics {

    private static Statistics instance;

    private List<Statistic> statistics;


    private Statistics(){statistics = new ArrayList<>();}

    public synchronized static Statistics getInstance(){
        if(instance==null)
            instance = new Statistics();
        return instance;
    }

    public List<Statistic> getStatistics(){return new ArrayList<>(statistics);}

    public void addStatistic(Statistic stat){
        statistics.add(stat);
    }

    public String stampStatistics(int n){

        int count = 0;
        StringBuilder result = new StringBuilder();
        for (Statistic statistic: statistics) {
            result.append("timestamp: ").append(statistic.getTimestamp()).append("\n")
                    .append("numeroConsegne: ").append(statistic.getNumeroConsegne()).append("\n")
                    .append("KmPercorsi: ").append(statistic.getKmPercorsi()).append("\n")
                    .append("inquinamento: ").append(statistic.getInquinamento()).append("\n")
                    .append("batteriaResidua: ").append(statistic.getBatteriaResidua()).append("\n\n");
            count ++;
            if (count == n)
                break;
        }
        return result.toString();
    }

    public String getMediaNumeroConsegneBetweenTimestamp(String timestamp1, String timestamp2) {

        int nConsegneTot = 0;
        int cont = 0;
        Timestamp t1 = Timestamp.valueOf(timestamp1);
        Timestamp t2 = Timestamp.valueOf(timestamp2);
        for (Statistic stat: statistics) {
            Timestamp timestampStat = Timestamp.valueOf(stat.getTimestamp());
            System.out.println("Consegne: " +  stat.getNumeroConsegne());
            if (timestampStat.after(t1) || timestampStat.equals(t1)){
                for (Statistic stat2: statistics) {
                    Timestamp timestampStat2 = Timestamp.valueOf(stat2.getTimestamp());
                    cont++;
                    nConsegneTot += stat2.getNumeroConsegne();

                    if (timestampStat2.before(t2) || timestampStat2.equals(t2))
                        break;
                }
            }
        }
        return "Consegne medie tra i due timestamp selezionati è: " + nConsegneTot/cont;
    }

    public String getMediaKMPercorsiBetweenTimestamp(String timestamp1, String timestamp2) {

        int nKmPercorsiTot = 0;
        int cont = 0;
        Timestamp t1 = Timestamp.valueOf(timestamp1);
        Timestamp t2 = Timestamp.valueOf(timestamp2);

        for (Statistic stat: statistics) {
            Timestamp timestampStat = Timestamp.valueOf(stat.getTimestamp());

            if (timestampStat.after(t1) || timestampStat.equals(t1)) {
                for (Statistic stat2 : statistics) {
                    Timestamp timestampStat2 = Timestamp.valueOf(stat2.getTimestamp());
                    cont++;
                    nKmPercorsiTot += stat2.getKmPercorsi();

                    if (timestampStat2.before(t2) || timestampStat2.equals(t2))
                        break;
                }
            }
        }
        return "Kilometri medi percorsi tra i due timestamp selezionati è: " + nKmPercorsiTot/cont;
    }
}
