package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.cluster.NodeRule;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultWorkerNodeSelectorTest {

    @Test
    public void test() {
        DefaultWorkerNodeSelector selector = new DefaultWorkerNodeSelector();

        SchedulingRule rule = new SchedulingRule();
        rule.setType("default");

        List<NodeInfo> nodeInfos = new ArrayList<>();
        NodeInfo node1 = new NodeInfo();
        node1.setId("node1");
        node1.setName("node1");

        NodeInfo node2 = new NodeInfo();
        node2.setId("node2");
        node2.setName("node2");
        node2.setRules(new NodeRule[]{NodeRule.SCHEDULER});
        nodeInfos.add(node1);
        nodeInfos.add(node2);

        List<NodeInfo> workers = selector.select(rule, nodeInfos);
        Assert.assertEquals(workers.size(), 1);
        Assert.assertEquals(workers.get(0), node1);
    }
}