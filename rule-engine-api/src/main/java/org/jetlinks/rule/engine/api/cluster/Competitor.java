package org.jetlinks.rule.engine.api.cluster;

import java.util.concurrent.CompletionStage;

public interface Competitor {

   <T> CompletionStage<T> compete(String key,T value);

}
