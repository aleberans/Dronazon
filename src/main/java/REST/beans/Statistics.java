package REST.beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
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

    public static synchronized String stampStatistics(List<Statistic> statistics){
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
}
