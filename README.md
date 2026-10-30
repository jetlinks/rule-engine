# 规则引擎

基于流程的流式规则引擎。

[![Maven Central](https://img.shields.io/maven-central/v/org.jetlinks/rule-engine.svg)](http://search.maven.org/#search%7Cga%7C1%7Crule-engine)
[![Maven metadata URL](https://img.shields.io/maven-metadata/v/https/oss.sonatype.org/content/repositories/snapshots/org/jetlinks/rule-engine/maven-metadata.xml.svg)](https://oss.sonatype.org/content/repositories/snapshots/org/jetlinks/rule-engine)
[![Build Status](https://travis-ci.com/jetlinks/rule-engine.svg?branch=master)](https://travis-ci.com/jetlinks/rule-engine)
[![codecov](https://codecov.io/gh/jetlinks/rule-engine/branch/master/graph/badge.svg)](https://codecov.io/gh/jetlinks/rule-engine)

# 函数执行器扩展

FunctionTaskExecutor 统一处理直接 execute 与订阅输入的可选执行观测。迁移节点开启 observeExecution，按需通过 tagObservation 追加标签；Recorder 生命周期、noop、父作用域和取消由原执行器复用 core Observations 负责。旧手工节点默认关闭公共观测，避免双记。prepareContext 在动作创建前恢复输入上下文，applyAsync 默认延迟调用 apply；平台仅在这些扩展点适配输入/输出载体，不再包装 Recorder。结果写出、完成事件、tracer 和 onError 保持原语义，不额外订阅业务，也不引入平台或本体依赖。

# 规则模型

```text
//规则模型
RuleModel{ 
    events:[ RuleLink ]     # 事件连接点,用于自定义规则事件的处理规则
    nodes:[ RuleNodeModel ] # 所有节点信息，包含事件节点
}
//节点模型
RuleNodeModel{
    executor: ""            # 节点执行器标识
    configuration: { Map }  # 节点配置
    events:[ RuleLink ]     # 事件连接点,用于自定义节点事件的处理规则
    inputs:[ RuleLink ]     # 输入连接点
    outputs:[ RuleLink ]    # 输出连接点
}
//连接点，将2个规则节点关联
RuleLink{
    type: ""                # 类型，为事件连接点时类型则为事件类型
    condition: Condition    # 连接条件
    source: RuleNodeModel   # 连接节点
    target: RuleNodeModel   # 被连接节点
}
//条件
Condition{
    type: ""                # 条件类型。如: expression
    configuration: { Map }  # 条件配置
}
```
