package mt.tools;

import mt.base.*;
import mt.decoder.util.*;
import mt.metrics.EvaluationMetric;
import mt.metrics.MetricFactory;
import mt.metrics.ScorerWrapperEvaluationMetric;
import mt.tune.GreedyMultiTranslationMetricMax;

import java.util.*;

/**
 * NBestArgmax utility - a command line tool for extracting the argmax 
 * translations from a n-best list for a given set of weights. 
 * 
 * The argmax translations can be optionally scored using a specified evaluation metric
 * and set of reference translations.
 * 
 * @author danielcer
 *
 */
public class NBestArgmax {
	
	static public void usage() {
		System.err.printf("Usage:\n\tjava mt.tool.NBestArgmax (nbest list) (wts file) [evaluationMetric:refs] > argmaxTrans 2> evaluationScore");
	}
	
	static public void main(String[] args) throws Exception {
		if (args.length != 2 && args.length != 3) {
			usage();
		}
		String nbestFilename = args[0]; String wtsFilename = args[1];
		String evalArg = args.length == 3 ? args[2] : null;
		
		MosesNBestList nbestlists = new MosesNBestList(nbestFilename);
		Scorer<String> wts = new StaticScorer(wtsFilename);
		
		EvaluationMetric<IString, String> eval = null;
	 
		if (evalArg != null) {
			String[] fields = evalArg.split(":");
			eval = MetricFactory.metric(fields[0], fields[1]);
		}
		GreedyMultiTranslationMetricMax<IString, String> argmaxByScore = new GreedyMultiTranslationMetricMax<IString, String>(new ScorerWrapperEvaluationMetric<IString, String>(wts));
		
		List<ScoredFeaturizedTranslation<IString, String>> argmaxTrans = argmaxByScore.maximize(nbestlists);
		
		for (ScoredFeaturizedTranslation<IString, String> trans : argmaxTrans) {
			System.out.println(trans.translation);
		}
		
		if (eval != null) System.err.printf("Eval score: %f\n", eval.score(argmaxTrans));			
	}
}
