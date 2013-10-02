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
			sb.append(patternName+": ");
			
			
			for(LinkedType lt: this.extractionParts){
				sb.append(lt.name().replace(this.patternName+".","")+":");
				sb.append(lt.text() + " ");
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
			//patternString = refactorPatterns(patternString);
			
			
			TaggerCollection t = TaggerCollection.fromString(patternString);
			List<Tagger> taggers = scala.collection.JavaConversions.asJavaList(t.taggers());
			String[] taggerDescriptors = new String[taggers.size()];
			for(int i = 0; i < taggerDescriptors.length; i++){
				taggerDescriptors[i] = taggers.get(i).name();
			}
									
			String inputFileString = FileUtils.readFileToString(new File(inputPath));
			
			
			
			PrintWriter pw = new PrintWriter(new File(outputPath));

			
			writeHeader(pw,taggerDescriptors);
	        OpenNlpChunker chunker = new OpenNlpChunker();
	        MorphaStemmer morpha = new MorphaStemmer();
		
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
	          //  List<Type> types = t.tag(scala.  tokens);
	            List<Type> types = scala.collection.JavaConversions.asJavaList(t.tag(scala.collection.JavaConversions.asScalaBuffer(tokens).seq()));

	            
	            
	            for(String level: taggerDescriptors){
		            pw.write("\t");
		            
            		List<Type> relevantTypes = new ArrayList<Type>();

	            	for(Type type: types){
						if(type.name().startsWith(level)){
	            			relevantTypes.add(type);
	            		}
	            	}
	            		
		            java.util.Collections.sort(relevantTypes, new Comparator<Type> (){
	            	public int compare(Type t1, Type t2){
	            		return t1.tokenInterval().compare(t2.tokenInterval());
	            	}
		            });
		            
		            for(Type type: relevantTypes){
		              if(type.name().equals(level)) pw.write(type.name()+"{"+type.tokenInterval()+":"+type.text()+"}" + " ");
		            }
	            		//if we are at the highest level write group matches as well
//            		if(taggerDescriptors[taggerDescriptors.length-1] == level){
//            			for(Type type: relevantTypes){
////		            		if(!type.name().equals(level)){
////		            			pw.write("\t"+type.name().substring(level.length()+1)+":"+type.text());
////		            		}
//		            		if(type instanceof LinkedType){		            			
//		            			if(((LinkedType) type).link().isDefined())
//		            				if(((LinkedType) type).link().get().name() == level)
//		            			        pw.write("\t"+type.name().substring(level.length()+1)+":"+type.text());
//		            		}
//            			}
//            			pw.write("\t");
//            		}
	            }
	            
	            Map<Type,List<LinkedType>> typeNamedGroupTypeMap = new HashMap<Type,List<LinkedType>>();
	            List<Extraction> extractions = new ArrayList<Extraction>();
	            
				for (Type typ : types) {
					if (typ instanceof LinkedType) {
						scala.Option<Type> parentLink = ((LinkedType) typ).link();
						if (parentLink.isDefined()) {
							if (typ.name().split("\\.").length > 1) {
								if (typ.name().split("\\.")[1].contains("T")) {
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
	
	
//	private static String findLevel(Type type, File taggerDirectory) throws IOException{
//		
//		List<String> typeDescriptors = new ArrayList<String>();
//		typeDescriptors.add(type.name());
//		if(type.source() != null){
//			typeDescriptors.add(type.source());
//		}
//		
//		for(File subDir : taggerDirectory.listFiles()){
//			if(subDir.isDirectory()){
//				String level = subDir.getName();
//				for(File x: subDir.listFiles()){
//					String xString = FileUtils.readFileToString(x);
//					for(String descriptionString : typeDescriptors){
//						if(xString.indexOf("descriptor=\""+descriptionString+"\">") != -1){
//							return level;
//						}
//						
//					}
//				}
//			}
//		}
//		
//		return "-1";
//	}
}
			
