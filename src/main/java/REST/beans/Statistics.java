package REST.beans;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
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

    public String getMediaNumeroConsegneBetweenTimestamp(Timestamp timestamp1, Timestamp timestamp2) {
        StringBuilder result = new StringBuilder();
        return result.toString();
    }
}
