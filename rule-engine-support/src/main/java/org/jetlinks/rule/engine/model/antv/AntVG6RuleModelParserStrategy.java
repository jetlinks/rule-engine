package org.jetlinks.rule.engine.model.antv;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.jetlinks.rule.engine.api.cluster.RunMode;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.jetlinks.rule.engine.api.model.Condition;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.model.RuleModelParserStrategy;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
public class AntVG6RuleModelParserStrategy implements RuleModelParserStrategy {
    @Override
    public String getFormat() {
        return "antv.g6";
    }

    @Override
    public RuleModel parse(String modelDefineString) {

        JSONObject jsonObject = JSONObject.parseObject(modelDefineString);
        RuleModel ruleModel = new RuleModel();

        //所有节点
        JSONArray nodes = jsonObject.getJSONArray("nodes");
        //连线
        JSONArray edges = jsonObject.getJSONArray("edges");

        Map<String, RuleNodeModel> allNodesMap = nodes.stream()
                .map(JSONObject.class::cast)
                .map(json -> {
                    RuleNodeModel model = new RuleNodeModel();
                    model.setId(Optional.ofNullable(json.getString("nodeId")).orElse(json.getString("id")));
                    model.setName(json.getString("label"));
                    Optional.ofNullable(json.getJSONObject("config"))
                            .ifPresent(model::setConfiguration);
                    model.setDescription(json.getString("remark"));
                    model.setEnd(json.getBooleanValue("isEnd"));
                    model.setStart(json.getBooleanValue("isStart"));
                    model.setExecutor(json.getString("executor"));
                    model.setSchedulingRule(Optional.ofNullable(json.getJSONObject("schedulingRule"))
                            .map(ruleJson -> ruleJson.toJavaObject(SchedulingRule.class)).orElse(null));

                    return model;
                })
                .collect(Collectors.toMap(RuleNodeModel::getId, Function.identity()));

        for (Object edge : edges) {
            JSONObject edgeJson = ((JSONObject) edge);
            boolean isEvent = edgeJson.getBooleanValue("isEvent");
            String source = edgeJson.getString("source");
            String target = edgeJson.getString("target");
            RuleNodeModel sourceModel = allNodesMap.get(source);
            RuleNodeModel targetModel = allNodesMap.get(target);
            if (sourceModel == null || targetModel == null) {
                continue;
            }
            RuleLink link = new RuleLink();
            link.setId(Optional.ofNullable(edgeJson.getString("nodeId")).orElse(edgeJson.getString("id")));
            Optional.ofNullable(edgeJson.getJSONObject("config"))
                    .ifPresent(link::setConfiguration);
            JSONObject conditionJson = edgeJson.getJSONObject("condition");
            if (null != conditionJson) {
                Condition condition = new Condition();
                condition.setType(conditionJson.getString("type"));
                condition.setConfiguration(conditionJson.getJSONObject("config"));
                link.setCondition(condition);
            }
            link.setType(edgeJson.getString("type"));
            link.setSource(sourceModel);
            link.setName(edgeJson.getString("label"));
            link.setDescription(edgeJson.getString("remark"));
            link.setTarget(targetModel);
            if (isEvent) {
                sourceModel.getEvents().add(link);
            } else {
                sourceModel.getOutputs().add(link);
                targetModel.getInputs().add(link);
            }
        }
        ruleModel.setNodes(new ArrayList<>(allNodesMap.values()));
        ruleModel.setId(jsonObject.getString("id"));
        ruleModel.setDescription(jsonObject.getString("remark"));
        ruleModel.setName(jsonObject.getString("name"));
        ruleModel.setRunMode(Optional.ofNullable(jsonObject.getString("runMode")).map(RunMode::valueOf).orElse(RunMode.CLUSTER));
        ruleModel.setSchedulingRule(Optional.ofNullable(jsonObject.getJSONObject("schedulingRule"))
                .map(json -> json.toJavaObject(SchedulingRule.class)).orElse(null));
        return ruleModel;
    }
}
