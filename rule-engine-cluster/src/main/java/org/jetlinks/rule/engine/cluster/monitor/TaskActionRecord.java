package org.jetlinks.rule.engine.cluster.monitor;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.core.monitor.recorder.ActionRecord;
import org.jetlinks.core.utils.SerializeUtils;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

@Getter
@Setter
public class TaskActionRecord extends ActionRecord {

    private String instanceId;

    private String nodeId;

    private String nodeName;

    private String executor;


    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        SerializeUtils.writeObject(instanceId, out);
        SerializeUtils.writeObject(nodeId, out);
        SerializeUtils.writeObject(nodeName,out);
        SerializeUtils.writeObject(executor, out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        instanceId = SerializeUtils.readObjectAs(in);
        nodeId = SerializeUtils.readObjectAs(in);
        nodeName = SerializeUtils.readObjectAs(in);
        executor = SerializeUtils.readObjectAs(in);
    }
}
