package DronazonPackage;

import SImulatori.Buffer;
import SImulatori.Measurement;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PM10Buffer implements Buffer {

    private List<Measurement> buffer = new ArrayList<>();
    private final PM10Trigger trigger;

    public PM10Buffer(PM10Trigger trigger){
        this.trigger = trigger;
    }

    @Override
    public void addMeasurement(Measurement m) {
        buffer.add(m);
        if (buffer.size() == 8)
            trigger.trigger(this);
    }

    @Override
    public List<Measurement> readAllAndClean() {
        List<Measurement> slide = buffer;
        buffer = buffer.stream().skip(4).collect(Collectors.toList());
        return slide;
    }
}
