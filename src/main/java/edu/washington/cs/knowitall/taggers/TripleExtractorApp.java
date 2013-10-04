package edu.washington.cs.knowitall.taggers;


import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.apache.commons.io.FileUtils;
import org.jdom2.JDOMException;



import edu.knowitall.taggers.TaggerCollection;
import edu.knowitall.taggers.tag.Tagger;
import edu.knowitall.tool.chunk.ChunkedToken;
import edu.knowitall.tool.chunk.OpenNlpChunker;
import edu.knowitall.tool.stem.Lemmatized;
import edu.knowitall.tool.stem.MorphaStemmer;
import edu.knowitall.tool.typer.Type;
import edu.knowitall.taggers.LinkedType;

public class TripleExtractorApp {
	
	
	/**
	 * Extraction rough draft includes name of pattern that matched
	 * and a list of the named groups that are linked to it.
	 * @author jgilme1
	 *
	 */
	public class Extraction{
		String patternName;
		List<LinkedType> extractionParts;
		
		public Extraction(String patternName, List<LinkedType> extractionParts){
			this.patternName = patternName;
			this.extractionParts = extractionParts;
		}
		
		@Override
		public String toString(){
			StringBuilder sb = new StringBuilder();
			sb.append(patternName+":");
			
			
			for(LinkedType lt: this.extractionParts){
				sb.append("\t");
				sb.append(lt.name().replace(this.patternName+".","")+":");
				sb.append(lt.text());
			}
			return sb.toString().trim();
		}
	}
	
	
	
	public static void main(String[] args) throws SecurityException, IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, JDOMException, IOException{
		if(args.length != 3){
			System.err.println("App takes exactly 3 arguments:\n" +
								"1. path to patterntagger specification file\n" +
								"2. path to input text file with one sentence per line\n" +
								"3. path to output file");
			return;
		}
			String taggerPath = args[0];
			String inputPath = args[1];
			String outputPath = args[2];
			
			String patternString = FileUtils.readFileToString(new File(taggerPath));
			
			
			
			//Create tagger collection from input pattern string
			TaggerCollection t = TaggerCollection.fromString(patternString);
			
			//get tagger names and store them in taggerDescriptors array
			List<Tagger> taggers = scala.collection.JavaConversions.asJavaList(t.taggers());
			String[] taggerDescriptors = new String[taggers.size()];
			for(int i = 0; i < taggerDescriptors.length; i++){
				taggerDescriptors[i] = taggers.get(i).name();
			}
									
			//load in input text
			String inputFileString = FileUtils.readFileToString(new File(inputPath));
			
			//initialize output with header
			PrintWriter pw = new PrintWriter(new File(outputPath));
			writeHeader(pw,taggerDescriptors);
			
			//initialize chunker and stemmer to pass parameters to the tag method
	        OpenNlpChunker chunker = new OpenNlpChunker();
	        MorphaStemmer morpha = new MorphaStemmer();
		
	        
	        //Treat each line in input doc as a sentence
			for(String line : inputFileString.split("\n")){
				
	            List<ChunkedToken> chunkedSentence = scala.collection.JavaConverters.seqAsJavaListConverter(chunker.chunk(line)).asJava();
	            List<Lemmatized<ChunkedToken>> tokens = new ArrayList<Lemmatized<ChunkedToken>>(chunkedSentence.size());
	            for (ChunkedToken token : chunkedSentence) {
	                Lemmatized<ChunkedToken> lemma = morpha.lemmatizeToken(token);
	                tokens.add(lemma);
	            }
	            pw.write(line.trim()+"\t");
	            for(Lemmatized<ChunkedToken> tok: tokens){
	            	pw.write(tok.token().postag()+" ");
	            }
	            
	            //get all of the matching types from the sentence
	            List<Type> types = scala.collection.JavaConversions.asJavaList(t.tag(scala.collection.JavaConversions.asScalaBuffer(tokens).seq()));

	            
	            //iterate over taggers in order
	            for(String level: taggerDescriptors){
		            pw.write("\t");
		            
            		List<Type> relevantTypes = new ArrayList<Type>();

            		//relevantTypes are Type matches that begin with the PatternName
	            	for(Type type: types){
						if(type.name().startsWith(level)){
	            			relevantTypes.add(type);
	            		}
	            	}
	            
	            	//sort types by order of their intervals
		            java.util.Collections.sort(relevantTypes, new Comparator<Type> (){
	            	public int compare(Type t1, Type t2){
	            		return t1.tokenInterval().compare(t2.tokenInterval());
	            	}
		            });
		            
		            //only output Types that match the taggerName exactly, so no groupTypes will be output here
		            for(Type type: relevantTypes){
		              if(type.name().equals(level)) pw.write(type.name()+"{"+type.tokenInterval()+":"+type.text()+"}" + " ");
		            }

	            }
	            
	            //initialize map for Type to linked children types 
	            //initialize list of extractions
	            Map<Type,List<LinkedType>> typeNamedGroupTypeMap = new HashMap<Type,List<LinkedType>>();
	            List<Extraction> extractions = new ArrayList<Extraction>();
	            
	            //iterate over all matching types of the input sentence
				for (Type typ : types) {
					// if the type has a parent
					if (typ instanceof LinkedType) {
						scala.Option<Type> parentLink = ((LinkedType) typ).link();
						if (parentLink.isDefined()) {
							//if the type contains a T this is just to check if it is a namedgroup instead of a numbered group
							//this can probably be checked with an instanceof call.
							if (typ.name().split("\\.").length > 1) {
								if (typ.name().split("\\.")[1].contains("T")) {
									//update the map from ParentTypes to NamedGroups.
									if (typeNamedGroupTypeMap.containsKey(parentLink.get())) {
										typeNamedGroupTypeMap.get(parentLink.get()).add((LinkedType) typ);
									} else {
										List<LinkedType> namedGroupTypes = new ArrayList<LinkedType>();
										namedGroupTypes.add((LinkedType) typ);
										typeNamedGroupTypeMap.put(parentLink.get(),namedGroupTypes);
									}
								}
							}
						}
					}
				}
	            
				
	            //turn map from parent types to children named group types into list of ordered extractions
	            for(String level: taggerDescriptors){
	            	for(Type typ : typeNamedGroupTypeMap.keySet()){
	            		if(typ.name().equals(level)){
	            			extractions.add(new TripleExtractorApp().new Extraction(typ.name(),typeNamedGroupTypeMap.get(typ)));
	            		}
	            	}
	            }
	            
	            //print extractions tab separated in line
	            for(Extraction extr: extractions){
	            	pw.write("\t"+extr.toString());
	            }
	            
	            
	            pw.write("\n");
			}				
			
			
			pw.close();
	}
	


	private static void writeHeader(PrintWriter pw, String[] taggerList){		 
		pw.write("sentence\tpos");
		
		for(String subDirName: taggerList){
			pw.write("\t"+subDirName+"-tags");
		}
		
		pw.write("\tPattern components\n");
	}
}
			
