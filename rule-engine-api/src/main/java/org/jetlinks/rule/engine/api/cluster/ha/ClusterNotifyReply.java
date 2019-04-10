package org.jetlinks.rule.engine.api.cluster.ha;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClusterNotifyReply implements Serializable {

    private String replyId;

    private String address;

    private Object reply;

    private boolean success;

    private String errorType;

    private String errorMessage;

}