package REST.beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Statistics {

    private static Statistics instance;

    private final List<Statistic> statistics;


    private Statistics(){statistics = new ArrayList<>();}

    public static synchronized Statistics getInstance(){
        if(instance==null)
            instance = new Statistics();
        return instance;
    }

    public synchronized List<Statistic> getStatistics(){return new ArrayList<>(statistics);}

    public synchronized void addStatistic(Statistic stat){
        statistics.add(stat);
    }

    public static String stampStatistics(List<Statistic> statistics){
        StringBuilder result = new StringBuilder();
        for (Statistic statistic: statistics) {
            result.append("timestamp: ").append(statistic.getTimestamp()).append("\n")
                    .append("numeroConsegne: ").append(statistic.getNumeroConsegne()).append("\n")
                    .append("KmPercorsi: ").append(statistic.getKmPercorsi()).append("\n")
                    .append("inquinamento: ").append(statistic.getInquinamento()).append("\n")
                    .append("batteriaResidua: ").append(statistic.getBatteriaResidua()).append("\n\n");
        }
        return result.toString();
    }

    public String getMediaNumeroConsegneBetweenTimestamp(String timestamp1, String timestamp2) {

        double nConsegneTot = 0;
        int cont = 0;
        Timestamp t1 = Timestamp.valueOf(timestamp1);
        Timestamp t2 = Timestamp.valueOf(timestamp2);
        for (Statistic stat: statistics) {
            Timestamp timestampStat = Timestamp.valueOf(stat.getTimestamp());
            if (timestampStat.after(t1) || timestampStat.equals(t1)){
                for (Statistic stat2: statistics) {
                    Timestamp timestampStat2 = Timestamp.valueOf(stat2.getTimestamp());
                    cont++;
                    nConsegneTot = nConsegneTot + stat2.getNumeroConsegne();

                    if (timestampStat2.before(t2) || timestampStat2.equals(t2))
                        break;
                }
            }
        }
        return "Consegne medie tra i due timestamp selezionati è: " + nConsegneTot/cont;
    }

    public String getMediaKMPercorsiBetweenTimestamp(String timestamp1, String timestamp2) {

        double nKmPercorsiTot = 0;
        int cont = 0;
        Timestamp t1 = Timestamp.valueOf(timestamp1);
        Timestamp t2 = Timestamp.valueOf(timestamp2);

        for (Statistic stat: statistics) {
            Timestamp timestampStat = Timestamp.valueOf(stat.getTimestamp());

            if (timestampStat.after(t1) || timestampStat.equals(t1)) {
                for (Statistic stat2 : statistics) {
                    Timestamp timestampStat2 = Timestamp.valueOf(stat2.getTimestamp());
                    cont++;
                    nKmPercorsiTot = nKmPercorsiTot + stat2.getKmPercorsi();

                    if (timestampStat2.before(t2) || timestampStat2.equals(t2))
                        break;
                }
            }
        }
        return "Kilometri medi percorsi tra i due timestamp selezionati è: " + nKmPercorsiTot/cont;
    }
}
