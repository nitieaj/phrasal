package mt.base;

import java.util.*;
import java.io.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import java.text.DecimalFormat;

import mt.decoder.util.Scorer;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.util.OAIndex;

/**
 * 
 * @author danielcer
 *
 */
public class MosesNBestList implements NBestListContainer<IString, String> {

  static public final String NBEST_SEP = " |||";

  private final List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLists;
	public final OAIndex<String> featureIndex = new OAIndex<String>();
	public static final String DEBUG_PROPERTY = "MosesNBestListDebug";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));


  @SuppressWarnings("unchecked")
	public MosesNBestList(NBestListContainer<IString, String> list1, NBestListContainer<IString, String> list2, Scorer scorer) {
		sequenceSelfMap = null;
		nbestLists = new ArrayList<List<ScoredFeaturizedTranslation<IString,String>>>(list1.nbestLists());
		
		List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLists2 = list2.nbestLists();
		for (int i = 0; i < nbestLists2.size(); i++) {
			((List)nbestLists.get(i)).addAll(nbestLists2.get(i));
			// rescore
			for (ScoredFeaturizedTranslation sft : nbestLists.get(i)) {
				sft.score = scorer.getIncrementalScore(sft.features);
			}
		}
	}
	
	public MosesNBestList(List<List<ScoredFeaturizedTranslation<IString,String>>> rawList) {
		sequenceSelfMap = null;
		nbestLists = new ArrayList<List<ScoredFeaturizedTranslation<IString,String>>>(rawList);
	}
	
	public final Map<Sequence<IString>, Sequence<IString>> sequenceSelfMap;
	
	public MosesNBestList(String filename) throws IOException {
		this(filename, new HashMap<Sequence<IString>, Sequence<IString>>());
	}
	
	public MosesNBestList(String filename, Map<Sequence<IString>, Sequence<IString>> sequenceSelfMap) throws IOException {
	  this.sequenceSelfMap = sequenceSelfMap;
		Runtime rt = Runtime.getRuntime();
		long preNBestListLoadMemUsed = rt.totalMemory()-rt.freeMemory();
		long startTimeMillis = System.currentTimeMillis();
		
		
		Map<String, String> featureNameSelfMap = new HashMap<String, String>();
		
		nbestLists = new ArrayList<List<ScoredFeaturizedTranslation<IString,String>>>();
		
		List<ScoredFeaturizedTranslation<IString,String>> currentNbest = new LinkedList<ScoredFeaturizedTranslation<IString,String>>();
		
		LineNumberReader reader;
		if (filename.endsWith(".gz")) {
			reader = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
		} else {
			reader = new LineNumberReader(new FileReader(filename));
		}
		Pattern tripplePipes = Pattern.compile(" \\|\\|\\| ");
		Pattern space = Pattern.compile(" ");
		Pattern label = Pattern.compile(".*:");
		String lastId = null;
		String[] emptyStringArray = new String[0];
		for (String inline; (inline = reader.readLine()) != null; ) {
				StringTokenizer toker = new StringTokenizer(inline);
				List<String> listFields = new LinkedList<String>();
				do {
					StringBuilder sb = new StringBuilder();
					boolean first = true;
				  do {
						String token = toker.nextToken();
					  if ("|||".equals(token)) break;
						if (!first) sb.append(" ");
						else first = false;
            sb.append(token);
				  }  while (toker.hasMoreTokens());
					listFields.add(sb.toString());
				}  while (toker.hasMoreTokens());
				
				String[] fields = listFields.toArray(emptyStringArray);

				// fields = tripplePipes.split(inline);
        if(fields.length < 3) {
          System.err.printf("Warning: bad nbest-list format: %s\n",inline);
          System.err.printf("Warning: expected at least 3 fields, but found only %d\n",fields.length);
          continue;
        }
				String id = fields[0];
				String translation = fields[1];
				String featuresStr = fields[2];
				String scoreStr = (fields.length >= 4 ? fields[3] : "0");
				String latticeIdStr = (fields.length >= 5 ? fields[4] : null);
				// System.err.printf("reading id: %s\n", id);
				if (lastId == null) {
					lastId = id;
				} else if (!id.equals(lastId)) {
					int intId = -1;
					try {
						intId = Integer.parseInt(id);
					}  catch (NumberFormatException e) {
						throw new RuntimeException(String.format("id '%s' can not be parsed as an integer value (line: %d)\n", id, reader.getLineNumber()));
					}
					int intLastId = Integer.parseInt(lastId);
					if (nbestLists.size() > intId) {
						throw new RuntimeException("n-best list ids are out of order");
					}
					
					while (nbestLists.size() < intLastId) {
					//	System.err.printf("Inserting empty: %d/%d\n", intLastId, nbestLists.size());
						nbestLists.add(new ArrayList<ScoredFeaturizedTranslation<IString,String>>());
					}
					
					// System.err.printf("Inserting NON-empty: %d/%d\n", intLastId, nbestLists.size());
					nbestLists.add(new ArrayList<ScoredFeaturizedTranslation<IString,String>>(currentNbest));
					currentNbest.clear();
					lastId = id;
					if (DEBUG) {
						System.err.printf("Doing %s Memory: %d MiB\n", id, (rt.totalMemory()-rt.freeMemory())/(1024*1024));
					}
				}
				
				double score;
				try {
					score = Double.parseDouble(scoreStr);
				} catch (NumberFormatException e) {
					throw new RuntimeException(String.format("Contents of score field, '%s', cannot be parsed as a double value. (line: %d, %s)", scoreStr, reader.getLineNumber(), filename));
				}
				
				long latticeId = -1;
				if (latticeIdStr != null) {
					try {
						latticeId = Long.parseLong(latticeIdStr);
					} catch (NumberFormatException e) {
						throw new RuntimeException(String.format("Contents of lattice id field, '%s', cannot be parsed as a long integer value (line: %d)", latticeIdStr, reader.getLineNumber()));
					}
				}
				
				String[] featureFields = space.split(featuresStr);
				String featureName = "unlabeled";
				Map<String, List<Double>> featureMap = new HashMap<String, List<Double>>();
				featureMap.put(featureName, new ArrayList<Double>());	
				//featureIndex.indexOf(featureName, true);
				for (String field : featureFields) {
					if (field.endsWith(":")) {
						featureName = field.substring(0, field.length()-1);
						featureMap.put(featureName, new ArrayList<Double>());
						continue;
					}
					try { 
						featureMap.get(featureName).add(new Double(field));
					} catch (NumberFormatException e) {
						throw new RuntimeException(String.format("Feature value, '%s', can not be parsed as a double value. (line: %d)", field, reader.getLineNumber()));
					}
				}
				
				List<FeatureValue<String>> featureValues = new FeatureValueArray<String>();

        for (String feature : featureMap.keySet()) {
          featureIndex.indexOf(feature, true);
					List<Double> values = featureMap.get(feature);
					if (values.size() == 1) {
						String featureNameStored = featureNameSelfMap.get(feature);
						if (featureNameStored == null) { 
							featureNameSelfMap.put(feature, feature);
							featureNameStored = feature;
						}
						featureValues.add(new FeatureValue<String>(featureNameStored, values.get(0), true));
					} else {
						for (int i = 0; i < values.size(); i++) {
							String composedName = feature + "_" + i;
							String featureNameStored = featureNameSelfMap.get(composedName);
							if (featureNameStored == null) { 
								featureNameSelfMap.put(composedName, composedName);
								featureNameStored = composedName;
							}	
							featureValues.add(new FeatureValue<String>(featureNameStored, values.get(i), true));
						}
					}
				}
				
				Sequence<IString> sequence = new RawSequence<IString>(IStrings.toIStringArray(space.split(translation)));
				Sequence<IString> sequenceStored = sequenceSelfMap.get(sequence);
				if (sequenceStored == null) {
					sequenceSelfMap.put(sequence, sequence);
					sequenceStored = sequence;
				}
				ScoredFeaturizedTranslation<IString,String> sfTrans;
        if (latticeId != -1) {
					sfTrans = new ScoredFeaturizedTranslation<IString,String>(sequenceStored, featureValues, score, latticeId);
				} else {
					sfTrans = new ScoredFeaturizedTranslation<IString,String>(sequenceStored, featureValues, score);
				}
				currentNbest.add(sfTrans);
		}
		
		int intLastId = (lastId == null ? -1 : Integer.parseInt(lastId));
		
		while (nbestLists.size() < intLastId) {
			// System.err.printf("Inserting empty: %d/%d\n", intLastId, nbestLists.size());
			nbestLists.add(new ArrayList<ScoredFeaturizedTranslation<IString,String>>());
		}
		
		nbestLists.add(new ArrayList<ScoredFeaturizedTranslation<IString,String>>(currentNbest));
		
		sequenceSelfMap = null;
		featureNameSelfMap = null;
		System.gc();
		
		long postNBestListLoadMemUsed = rt.totalMemory() - rt.freeMemory();
		long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
		System.err.printf("Done loading Moses n-best lists: %s (mem used: %d MiB time: %.3f s)\n", filename, 
				(postNBestListLoadMemUsed - preNBestListLoadMemUsed)/(1024*1024), loadTimeMillis/1000.0);
		
		reader.close();
	}

  public void setArraysFromIndex() {

    Runtime rt = Runtime.getRuntime();
    rt.gc();
    long startMemUsed = rt.totalMemory()-rt.freeMemory();
		long startTimeMillis = System.currentTimeMillis();

    for(List<ScoredFeaturizedTranslation<IString,String>> nbestList : nbestLists) {
      for(ScoredFeaturizedTranslation<IString,String> trans : nbestList) {
        List<FeatureValue<String>> fvl = trans.features;
        if(fvl instanceof FeatureValueArray) {
          FeatureValueArray<String> fva = (FeatureValueArray<String>) fvl;
          fva.setArrayFromIndex(featureIndex);
        }
      }
    }

    rt.gc();
    long endMemUsed = rt.totalMemory() - rt.freeMemory();
		long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
		System.err.printf("Done generating feature arrays for n-best lists (mem used: %d MiB time: %.3f s)\n", 
				(endMemUsed - startMemUsed)/(1024*1024), loadTimeMillis/1000.0);
  }

  @Override
	public List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLists() {
		return nbestLists;
	}
	
	@Override
	public String toString() {
    return printVerboseFormat();
  }

  public String printVerboseFormat() {
		StringBuffer sbuf = new StringBuffer();
		sbuf.append("Moses N-Best List:\n");
		sbuf.append("----------------------\n");
		for (int i = 0; i < nbestLists.size(); i++) {
			sbuf.append("List: ").append(i).append(" (entries: ").append(nbestLists.get(i).size()).append("):\n");
			for (int j = 0; j < nbestLists.get(i).size(); j++) {
				sbuf.append(j).append(": Sequence: ").append(nbestLists.get(i).get(j).translation).append(" Score: ").append(nbestLists.get(i).get(j).score);
				sbuf.append(" Features:");
				for (FeatureValue<String> fv : nbestLists.get(i).get(j).features) {
					sbuf.append(" ").append(fv);
				}
				sbuf.append("\n");
			}
		}
		return sbuf.toString();
	}

	public String printMosesFormat() {
    DecimalFormat df = new DecimalFormat("0.####E0");
    StringBuffer sbuf = new StringBuffer();
		for (int i = 0; i < nbestLists.size(); i++) {
			for (int j = 0; j < nbestLists.get(i).size(); j++) {
        ScoredFeaturizedTranslation<IString,String> tr = nbestLists.get(i).get(j);
        sbuf.append(i).append(NBEST_SEP).append(' ').append(tr.translation).append(NBEST_SEP);
				for (FeatureValue<String> fv : tr.features) {
          sbuf.append(' ').append(fv.name).append(": ").append((fv.value == (int)fv.value ? (int)fv.value : df.format(fv.value)));
				}
        if(tr.score != 0.0)
          sbuf.append(NBEST_SEP).append(' ').append(df.format(tr.score));
        if (tr.latticeSourceId != -1) {
          sbuf.append(NBEST_SEP).append(' ');
          sbuf.append(tr.latticeSourceId);
        }
        sbuf.append("\n");
			}
		}
		return sbuf.toString();
  }

  static public void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.printf("Usage:\n\tjava ...(Moses style nbest list)\n");
			System.exit(-1);
		}
		
		String nbestListFilename = args[0];
		MosesNBestList nbestList = new MosesNBestList(nbestListFilename);
		System.out.print(nbestList.printMosesFormat());
	}

	public static String escape(String featureName) {
		return featureName.replaceAll("\\\\", "\\\\").replaceAll(" ", "\\_");
	}
}
