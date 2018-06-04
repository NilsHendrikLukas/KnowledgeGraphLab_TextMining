import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.util.CoreMap;

import org.apache.jena.rdf.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public class OpenIEDemo {



    public static void main(String[] args) throws Exception {

        // Read in all files and concatenate their content
        List<String> fileContents = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get("./data/"))) {
            paths.filter(Files::isRegularFile)
                .forEach((path) ->{
                    List<String> content = null;
                    if(fileContents.isEmpty()) {        // Remove this when done (reads only 1 file)
                    System.out.println(path);
                    try {
                        String allLines = Files.readAllLines(path).toString();
                        System.out.println("Before: " + allLines);
                        allLines = allLines.replaceAll("<[^>]+>", "");
                        System.out.println("After: " + allLines);

                        fileContents.add(allLines);
                        System.out.println(fileContents.get(0));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }}
                });
        }

        // Create the Stanford CoreNLP pipeline
        Properties props = new Properties();

        // tokenize: Read tokens (words) from text
        // ssplit: Split a sequence of tokens into sentences
        // pos: Part-of-Speech annotator. Appends POS tags to tokens.
        // lemma: Lemmatization. Projects words of same morphology to the same base word (e.g. flying, flys -> fly)
        // depparse: Syntactic Dependency Parser (which words refer to which other words)
        // natlog: Natural logic annotator. Marks scope of words and polarity (important for OpenIE!)
        // openie: Extracts triples from text with a confidence score
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,natlog,entitymentions,coref,openie");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Annotation doc = new Annotation(fileContents.toString());
        pipeline.annotate(doc);

        /*
            (entitymentions) List all entity mentions
         */
        System.out.println("---------------------");
        System.out.println("Entity Mentions: ");
        System.out.println("---------------------");
        List<CoreMap> multiWordsExp = doc.get(CoreAnnotations.MentionsAnnotation.class);
        for (CoreMap multiWord : multiWordsExp) {
            String custNERClass = multiWord.get(CoreAnnotations.NamedEntityTagAnnotation.class);
            System.out.println(multiWord +" : " +custNERClass);
        }

        System.out.println("---------------------");
        System.out.println("Coreference Chain: ");
        System.out.println("---------------------");
        /*
            (coref) List coreference chain
         */
        for (CorefChain cc : doc.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
            System.out.println("\t" + cc);
        }
        for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
            System.out.println("---");
            System.out.println("mentions");
            for (Mention m : sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
                System.out.println("\t" + m);
            }
        }
        System.out.println("---------------------");
        System.out.println("OpenIE: ");
        System.out.println("---------------------");

        ArrayList<Collection<RelationTriple>> allTriples = new ArrayList<>();
        // Iterate through each sentence
        for(CoreMap sentence:  doc.get(CoreAnnotations.SentencesAnnotation.class)) {

            for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                // this is the text of the token
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                // this is the POS tag of the token
                String posTag = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                // this is the NER label of the token
                String nerLabel = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                //System.out.println("Token: " + word + " named entity: " + nerLabel);
            }

            // Obtain the OpenIE Triples
            Collection<RelationTriple> triples =
                    sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
            allTriples.add(triples);
            for (RelationTriple triple : triples) {
                System.out.println("[" + triple.confidence + "]: " + "(S, " +
                        triple.subjectLemmaGloss() + "), (R, " +
                        triple.relationLemmaGloss() + "), (O, " +
                        triple.objectLemmaGloss() + ")");
            }
        }

        System.out.println("---------------------");
        System.out.println("Write to Jena ");
        System.out.println("---------------------");
        // Write triples to Jena
        // All triples are stored in {allTriples}
        Model model = ModelFactory.createDefaultModel();

        for (Collection<RelationTriple> documentTriples : allTriples) {
            for(RelationTriple triple : documentTriples) {
                CoreLabel S =  triple.subjectHead();
                CoreLabel R = triple.relationHead();
                CoreLabel O = triple.objectHead();

                // Write triple to Jena
                Resource sRes = model.createResource(S.toString());
                sRes.addProperty(ResourceFactory.createProperty(R.toString()), O.toString());

                System.out.println("Triples : (" + S  + " ||| " + R + " ||| " + O + " ||| " + ")");
            }
        }

        // list the statements in the Model
        StmtIterator iter = model.listStatements();

        // print out the predicate, subject and object of each statement
        while (iter.hasNext()) {
            Statement stmt      = iter.nextStatement();  // get next statement
            Resource  subject   = stmt.getSubject();     // get the subject
            Property  predicate = stmt.getPredicate();   // get the predicate
            RDFNode   object    = stmt.getObject();      // get the object

            System.out.print(subject.toString());
            System.out.print(" " + predicate.toString() + " ");
            if (object instanceof Resource) {
                System.out.print(object.toString());
            } else {
                // object is a literal
                System.out.print(" \"" + object.toString() + "\"");
            }

            System.out.println(" .");
        }
    }
}
