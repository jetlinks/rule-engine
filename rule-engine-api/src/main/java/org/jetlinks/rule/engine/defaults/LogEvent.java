package org.jetlinks.rule.engine.defaults;

import lombok.*;
import org.jetlinks.core.utils.SerializeUtils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogEvent implements Externalizable {

    private String instanceId;

    private String nodeId;

    private String workerId;

    private String level;

    private String message;

    private String exception;

    private long timestamp;

    private String traceId;

    private String spanId;

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(instanceId);
        out.writeUTF(nodeId);
        out.writeUTF(workerId);
        out.writeUTF(level);
        SerializeUtils.writeObject(message, out);
        SerializeUtils.writeObject(exception, out);
        out.writeLong(timestamp);

        SerializeUtils.writeNullableUTF(traceId, out);
        SerializeUtils.writeNullableUTF(spanId, out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        instanceId = in.readUTF();
        nodeId = in.readUTF();
        workerId = in.readUTF();
        level = in.readUTF();
        message = (String) SerializeUtils.readObject(in);
        exception = (String) SerializeUtils.readObject(in);
        timestamp = in.readLong();

        traceId = SerializeUtils.readNullableUTF(in);
        spanId = SerializeUtils.readNullableUTF(in);
    }
}
