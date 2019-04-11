package org.jetlinks.rule.engine.model.xml;

import lombok.SneakyThrows;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.rule.engine.api.cluster.RunMode;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.jetlinks.rule.engine.api.model.Condition;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.model.RuleModelParserStrategy;
import org.springframework.util.StringUtils;

import java.io.StringReader;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class XmlRuleModelParserStrategy implements RuleModelParserStrategy {
    @Override
    public String getFormat() {
        return "re.xml";
    }

    @Override
    @SneakyThrows
    public RuleModel parse(String modelDefineString) {
        SAXReader reader = new SAXReader();
        Document document = reader.read(new StringReader(modelDefineString));

        List<PrepareRuleNode> prepareRuleNodeList = new ArrayList<>();

        Element rule = document.getRootElement();

        RuleModel ruleModel = new RuleModel();
        ruleModel.setId(rule.attributeValue("id"));
        ruleModel.setName(rule.attributeValue("name"));
        ruleModel.setDescription(rule.attributeValue("description"));
        ruleModel.setRunMode(Optional.ofNullable(rule.attributeValue("runMode")).map(RunMode::valueOf).orElse(RunMode.CLUSTER));
        //预处理所有节点信息
        for (Iterator<Element> it = rule.elementIterator("node"); it.hasNext(); ) {
            Element element = it.next();
            prepareRuleNodeList.add(parseNode(element));
        }
        //先创建所有的节点模型基础信息
        Map<String, RuleNodeModel> nodes = prepareRuleNodeList
                .stream()
                .map(PrepareRuleNode::toRuleNodeModel)
                .collect(Collectors.toMap(RuleNodeModel::getId, Function.identity()));

        //关联节点
        for (PrepareRuleNode node : prepareRuleNodeList) {
            if (StringUtils.isEmpty(node.getId())) {
                throw new NullPointerException("节点id不能为空");
            }
            RuleNodeModel model = nodes.get(node.getId());
            model.setRuleId(ruleModel.getId());
            //output
            for (PrepareLink output : node.outputs) {
                RuleNodeModel outputNode = nodes.get(output.target);
                if (null != outputNode) {
                    RuleLink link = new RuleLink();
                    if (StringUtils.hasText(output.id)) {
                        link.setId(output.id);
                    } else {
                        link.setId(model.getId() + "-link-" + outputNode.getId());
                    }
                    link.setType(output.type);
                    link.setCondition(output.condition);
                    link.setTarget(outputNode);
                    link.setSource(model);
                    link.setConfiguration(output.configuration);
                    outputNode.getInputs().add(link);
                    model.getOutputs().add(link);
                }
            }
            //events
            for (PrepareLink event : node.events) {
                RuleNodeModel outputNode = nodes.get(event.target);
                if (null != outputNode) {
                    RuleLink link = new RuleLink();
                    if (StringUtils.hasText(event.id)) {
                        link.setId(event.id);
                    } else {
                        link.setId(model.getId() + "-link-" + outputNode.getId());
                    }
                    link.setType(event.type);
                    link.setCondition(event.condition);
                    link.setTarget(outputNode);
                    link.setSource(model);
                    model.getEvents().add(link);
                    outputNode.getInputs().add(link);
                }
            }
        }
        ruleModel.getNodes().addAll(nodes.values());
        return ruleModel;
    }

    private PrepareRuleNode parseNode(Element element) {
        PrepareRuleNode node = new PrepareRuleNode();

        //节点属性信息
        node.nodeProperties = element.attributes()
                .stream()
                .collect(Collectors.toMap(Attribute::getName, Attribute::getValue));

        //调度配置
        Element schedulingRule = element.element("scheduling-rule");

        if (null != schedulingRule) {
            SchedulingRule rule = new SchedulingRule();
            String type = schedulingRule.attributeValue("type");
            rule.setType(type);
            rule.setConfiguration(schedulingRule.elements()
                    .stream()
                    .collect(Collectors.toMap(Element::getName, Element::getTextTrim)));
            node.nodeProperties.put("schedulingRule", schedulingRule);
        }

        //配置信息
        element.element("configuration")
                .elementIterator()
                .forEachRemaining(e -> node.configuration.put(e.getName(), e.getTextTrim()));

        Optional.ofNullable(element.element("outputs"))
                .map(e -> e.elements("output"))
                .ifPresent(lst -> node
                        .outputs
                        .addAll(lst.stream()
                                .map(this::parseLink)
                                .collect(Collectors.toList())));

        Optional.ofNullable(element.element("events"))
                .map(e -> e.elements("event"))
                .ifPresent(lst -> node
                        .events
                        .addAll(lst.stream()
                                .map(this::parseLink)
                                .collect(Collectors.toList())));
        return node;
    }

    private PrepareLink parseLink(Element element) {
        PrepareLink link = new PrepareLink();
        link.configuration = element.elements()
                .stream()
                .collect(Collectors.toMap(Element::getName, Element::getTextTrim));

        link.id = element.attributeValue("id");
        link.target = element.attributeValue("target");
        link.type = element.attributeValue("type");

        Element conditionEl = element.element("condition");
        if (conditionEl != null) {
            Element configuration = element.element("condition");
            link.condition = new Condition();
            link.condition.setType(conditionEl.attributeValue("type"));
            if (null != configuration) {
                link.condition.setConfiguration(configuration.elements().stream()
                        .collect(Collectors.toMap(Element::getName, Element::getTextTrim)));
            }
        }

        return link;
    }

    private class PrepareRuleNode {

        private String getId() {
            return (String) nodeProperties.get("id");
        }

        private Map<String, Object> nodeProperties = new HashMap<>();

        private Map<String, Object> configuration = new HashMap<>();

        private List<PrepareLink> outputs = new ArrayList<>();

        private List<PrepareLink> events = new ArrayList<>();

        public RuleNodeModel toRuleNodeModel() {
            RuleNodeModel ruleNodeModel = FastBeanCopier.copy(nodeProperties, new RuleNodeModel());
            ruleNodeModel.setConfiguration(configuration);
            return ruleNodeModel;
        }
    }

    private class PrepareLink {
        private String              id;
        private String              type;
        private Map<String, Object> configuration = new HashMap<>();
        private String              target;
        private Condition           condition;
    }
}
