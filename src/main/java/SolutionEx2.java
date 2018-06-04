import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.util.CoreMap;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Resource;

import java.util.*;

/**
 * Stanford Core NLP technical documentation:
 * @see: <a href="https://nlp.stanford.edu/nlp/javadoc/javanlp/index.html?overview-summary.html">Doc</a>
 */
public final class SolutionEx2 {
    // SPARQL endpoint of DBpedia
    private static String DBPEDIA_SPARQL_ENDPOINT = "http://dbpedia.org/sparql";

    // The data we will be processing in this exercise
    private static String textContent = "Barack Hussein Obama (born August 4, 1961) is an American politician who served as " +
            "the 44th President of the United States from January 20, 2009 to January 20, 2017. He is the only " +
            "President who was born in Hawaii and the only President who was born outside of the contiguous 48 states."+
            "Obama was born in 1961 in Honolulu. He was born to a white mother and a black father. His mother, Ann " +
            "Dunham (1942–1995), was born in Wichita, Kansas. She was mostly of English descent. His father, Barack " +
            "Obama Sr. (1936–1982), was a married Luo Kenyan man from Nyang'oma Kogelon. In 2008, Obama was nominated " +
            "for president a year after his campaign began and after a close primary campaign against Hillary " +
            "Clinton. He was elected over Republican John McCain and was inaugurated on January 20, 2009. Nine months" +
            " later, Obama was named the 2009 Nobel Peace Prize laureate.";


    public static void main(String[] args) {
        // Create the Stanford CoreNLP pipeline
        Properties props = new Properties();

        // tokenize: Read tokens (words) from text
        // ssplit: Split a sequence of tokens into sentences
        // pos: Part-of-Speech annotator. Appends POS tags to tokens.
        // lemma: Lemmatization. Projects words of same morphology to the same base word (e.g. flying, flys -> fly)
        // depparse: Syntactic Dependency Parser (which words refer to which other words)
        // natlog: Natural logic annotator. Marks scope of words and polarity (important for OpenIE!)
        // openie: Extracts triples from text with a confidence score
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref,natlog,openie");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        // Give the annotators as much information as possible and use a statistical algorithm which requires much
        // lesser processing power (also we need depparse only)
        props.setProperty("coref.algorithm", "statistical");
        // extract triples only if they consume the entire fragment
        props.setProperty("openie.triple.strict", "true");
        // run coreference and replace pronominal mentions with their canonical mention in the text.
        props.setProperty("openie.resolve_coref", "true");

        // Annotate the document, but use the Annotation wrapper 'CoreDocument' instead of the base 'Annotation' class
        CoreDocument annotatedDoc = new CoreDocument(textContent);
        pipeline.annotate(annotatedDoc);

        System.out.println("–– Task b) Named Entities ––");
        taskB(annotatedDoc);

        System.out.println("–– Task c) Coreference Annotations ––");
        taskC(annotatedDoc);

        System.out.println("–– Task d) RDF Triples with OpenIE ––");
        taskD(annotatedDoc);

        System.out.println("–– Task e) Entity Resolution ––");
        taskE(annotatedDoc);

        /*
         * At this point, we have access to the following fields:
         * 1.) The resolved DBpedia entities
         * 2.) The OpenIE triples from this specific text
         * 3.) The entity types resolved by the coreference annotator
         *
         * Ideally, we would look up an existing graph that stores information about entities (e.g. DBpedia)
         * and check with the context (again provided by the coreference chain) whether the existing data really
         * matches the resolved entity.
         */
    }

    /**
     * Extract entities from sentence via CoreEntitiyMentions (convenient wrapper class)
     * @param annotatedDoc Document that contains all annotations
     */
    private static void taskB(CoreDocument annotatedDoc) {
        for (CoreSentence sentence : annotatedDoc.sentences()) {
            for (CoreEntityMention entity : sentence.entityMentions()) {
                System.out.println(entity.canonicalEntityMention() + " : " + entity.entityType());
            }
        }
    }

    /** Print the ontology to the standard output
     * Establish fitting prefixes for this ontology e.g. owl, rdfs, rdf (..)
     * and use rdf:type, rdfs:subClassOf, owl:Class, rdfs:label and incorporate as much info as provided
     * @param annotatedDoc Document that contains all annotations
     */
    private static void taskC(CoreDocument annotatedDoc) {
        // Start with the prefixes
        System.out.println("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>");
        System.out.println("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>");
        System.out.println("PREFIX owl: <http://www.w3.org/2002/07/owl#>");
        // Extract triples and basic class properties
        for (CoreSentence sentence : annotatedDoc.sentences()) {
            for (CoreEntityMention entity : sentence.entityMentions()) {

                // Extract the label and type from the mentioned entity
                String entityLabel = entity.toString();
                String entityType = entity.entityType();

                // Match all entity types and make them owl classes (duplicates don't matter)
                System.out.println(entityType + " rdf:type owl:Class.");

                // Make all entities classes and assign them to the their entity type class
                // For debugging, put the occurring sentence as a comment
                System.out.println(entityLabel + " rdf:type owl:Class ;");
                System.out.println("               rdfs:subClassOf :" + entityType + " ;");
                System.out.println("               rdfs:comment " + sentence.toString() + " .");
            }
        }
    }

    /**
     * Using the OpenIE annotator simplifies the final task marginally because it provides us with triples
     * already. The coreference annotation remains important because some triples may still contain unresolved
     * words such as "she", "he".
     * @param annotatedDoc Document that contains all annotations
     */
    private static void taskD(CoreDocument annotatedDoc) {
        for (CoreMap sentence : annotatedDoc.annotation().get(CoreAnnotations.SentencesAnnotation.class)) {
            // Get the OpenIE triples for the sentence
            Collection<RelationTriple> triples =
                    sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
            // Print the triples
            for (RelationTriple triple : triples) {
                System.out.println(triple.confidence + "\t" +
                        triple.subjectLemmaGloss() + "\t" +
                        triple.relationLemmaGloss() + "\t" +
                        triple.objectLemmaGloss());
            }
        }
    }

    /**
     *  Formulates a SPARQL queries and resolve the label of the query. Greedily select the first possible
     * result. In our simplistic approach, context should be embedded into a query, e.g. one could first resolve all
     * possibilities for the entity type with SPARQL queries and then append them into a series of OPTIONAL queries
     * and rank according to that.
     * Ideally, we would want to combine context from different sources and add context to each query.
     * @param annotatedDoc: Document that contains all annotations
     */
    private static void taskE(CoreDocument annotatedDoc) {
        // Collect a list with all mentioned entities
        List<CoreEntityMention> entityMentions = annotatedDoc.entityMentions();

        for( CoreEntityMention entityMention : entityMentions) {
            // Formulate a SPARQL query for each entity and send it to DBpedia
            String entityLabel = entityMention.toString();
            String queryText = formulateQuery(entityLabel);

            // Feed the query into the Jena query engine
            Query query = QueryFactory.create(queryText);
            try{
                QueryExecution queryExecution = QueryExecutionFactory.sparqlService(DBPEDIA_SPARQL_ENDPOINT, query);
                // 40s timeout
                queryExecution.setTimeout(40000);
                // Execute the select statement (this is a blocking call)
                ResultSet results = queryExecution.execSelect();

                while(results.hasNext()) {
                    QuerySolution sol = results.next();
                    Resource subject = sol.getResource("name");
                    System.out.println("'" + entityLabel + "' was resolved to " + subject.toString());
                }
                queryExecution.close();
            }catch(Exception e){
                System.err.println("[ERROR] Please check your internet connection!");
                System.out.println(e.toString());
            }
        }
    }

    /**
     * Helper function that inserts arguments into query and returns a valid SPARQL query
     * @param entityName: Name of the entity whose label should be matched
     * @return Valid SPARQL query for the label of that entity
     */
    private static String formulateQuery(String entityName) {
        String query =
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "SELECT ?name\n" +
                "WHERE {\n" +
                "?name rdfs:label ?label .\n" +
                "FILTER regex(?label, '/arg0/', 'i' ) .\n" +
                "}\n" +
                "LIMIT 1";

        // Place them into an array for extensibility in the future
        String[] args = {entityName};
        // Replace all '/arg{index}/' with the associated argument
        for(int i = 0;i < args.length; i++) {
            query = query.replaceAll("/arg" + i + "/", args[i]);
        }
        return query;
    }

}