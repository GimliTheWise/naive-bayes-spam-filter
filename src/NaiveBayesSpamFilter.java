import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class NaiveBayesSpamFilter {
    private static Map<String, Double> anl_occurrences_ham;
    private static Map<String, Double> anl_occurrences_spam;
    private static final double ALPHA = 0.06;
    private static final double THRESHOLD = 0.95;
    private static final String DATASET = "test"; //kallibrierung or test
    private static final String DEFAULT_PATH = "./resources/";


    public static void main(String[] args) throws IOException {
        System.out.println("Trying to read mails...");

        // read mails from directory into list
        List<File> spam_anl_mails = getMails(Paths.get(DEFAULT_PATH, "spam-anlern"));
        List<File> ham_anl_mails = getMails(Paths.get(DEFAULT_PATH, "ham-anlern"));
        List<File> spam_test_mails = getMails(Paths.get(DEFAULT_PATH, "spam-" + DATASET));
        List<File> ham_test_mails = getMails(Paths.get(DEFAULT_PATH, "ham-" + DATASET));

        System.out.println("counting occurrences of words in ham and spam anlern-datasets...");

        // count how many times words are found in all anlern mails
        // do this for ham and spam
        anl_occurrences_ham = wordOccurrenceCounter(ham_anl_mails);
        anl_occurrences_spam = wordOccurrenceCounter(spam_anl_mails);

        System.out.println("making sure all words occur in ham and spam.");
        System.out.println("Absent words being added with alpha=" + ALPHA + " occurrence...");
        anl_occurrences_ham.forEach((s, aDouble) -> anl_occurrences_spam.putIfAbsent(s, ALPHA));
        anl_occurrences_spam.forEach((s, aDouble) -> anl_occurrences_ham.putIfAbsent(s, ALPHA));

        System.out.println("Threshold for spam classification set at: " + THRESHOLD * 100 + "%");
        System.out.println("calculating spam probabilities on " + DATASET + " dataset...");
        System.out.println();


        // classify spam dataset
        Map<String, Double> classifiedMails = classifyMails(spam_test_mails);
        double correct_class = classifiedMails.values().stream().filter(aDouble -> aDouble >= THRESHOLD).count();
        System.out.printf("Correct spam classification ratio: %.2f%%", (correct_class / classifiedMails.size()) * 100);
        System.out.println();
        // classify ham dataset
        classifiedMails = classifyMails(ham_test_mails);
        correct_class = classifiedMails.values().stream().filter(aDouble -> aDouble < THRESHOLD).count();
        System.out.printf("Correct ham classification ratio: %.2f%%", (correct_class / classifiedMails.size()) * 100);
    }


    /**
     * classify the spam probability for each mail based on the containing words.
     * @param mails as a list of files
     * @return a dictionary of mail name mapped to its probability of being spam
     * @throws FileNotFoundException if mail path is wrong
     */
    private static Map<String, Double> classifyMails(List<File> mails) throws FileNotFoundException {
        Map<String, Double> classifiedMails = new HashMap<>();
        for (File mail :
                mails) {
            List<String> mail_words = mailWords(mail);
            double mail_spaminess = spaminess(mail_words);
            classifiedMails.put(mail.getName(), mail_spaminess);
        }
        return classifiedMails;
    }

    /**
     * gets all mails as files from a location on the file system
     * @param path to the mails
     * @return a list of files
     * @throws IOException if mail path is wrong
     */
    static List<File> getMails(Path path) throws IOException {
        return Files.walk(path)
                .filter(Files::isRegularFile)
                .map(Path::toFile).toList();
    }


    /**
     * counts how many times words occur in all mails
     * @param mails to search in
     * @return a dictionary of words with their occurrences in the mails
     * @throws FileNotFoundException if mail path is wrong
     */
    static Map<String, Double> wordOccurrenceCounter(List<File> mails) throws FileNotFoundException {
        Map<String, Double> word_occurrences = new HashMap<>();
        Scanner mailReader;
        for (File mail :
                mails) {
            mailReader = new Scanner(mail);
            while (mailReader.hasNextLine()) {
                String data = mailReader.nextLine();
                String[] words = data.split(" ");
                for (String word :
                        words) {
                    word = word.toLowerCase().trim();
                    if (word_occurrences.containsKey(word)) word_occurrences.merge(word, 1.0, Double::sum);
                    else word_occurrences.put(word, 1.0);
                }
            }
            mailReader.close();
        }
        return word_occurrences;
    }

    /**
     * go threw the mail and find all the words by splitting the string by spaces
     * @param mail to find words in
     * @return a list of words as strings
     * @throws FileNotFoundException if path is incorrect
     */
    static List<String> mailWords(File mail) throws FileNotFoundException {
        Scanner mailReader;
        List<String> words = new ArrayList<>();
        mailReader = new Scanner(mail);
        while (mailReader.hasNextLine()) {
            String data = mailReader.nextLine();
            words.addAll(Arrays.stream(data.split(" ")).toList());
        }
        mailReader.close();
        return words.stream().map(s -> s.toLowerCase().trim()).toList();
    }

    /**
     * Calculates the probability of a mail being spam given its containing words.
     * The formula used is not biased and taken from here:
     * <a href="https://www.math.kit.edu/ianm4/~ritterbusch/seite/spam/de">...</a>
     * @param words of a mail as a list of strings
     * @return a double between 0 and 1
     */
    static double spaminess(List<String> words) {
        double Pr_W_given_that_S = 0.5; // change this to bias the formula
        double Pr_W_given_that_H = 1-Pr_W_given_that_S;
        double spam_words_sum = anl_occurrences_spam.values().stream().mapToDouble(Double::doubleValue).sum();
        double ham_words_sum = anl_occurrences_ham.values().stream().mapToDouble(Double::doubleValue).sum();

        // saves the times PrWS or PrWH is multiplied with 10 to the power of 100.
        int pow_S = 0;
        int pow_H = 0;
        double pow_jump = 100;

        for (String word :
                words) {
            // only take words into account which are present in the anlern-dataset.
            if (anl_occurrences_spam.containsKey(word) && anl_occurrences_ham.containsKey(word)) {

                // multiply each word probability with each other.
                Pr_W_given_that_S = Pr_W_given_that_S * (anl_occurrences_spam.get(word) / spam_words_sum);
                Pr_W_given_that_H = Pr_W_given_that_H * (anl_occurrences_ham.get(word) / ham_words_sum);

                // make sure no underflows occur (numbers getting too small).
                // if double gets to small multiply it with 10 to the power of 100 and increase the counter.
                if (Pr_W_given_that_S < Math.pow(10, -pow_jump)) {
                    Pr_W_given_that_S = Pr_W_given_that_S * Math.pow(10, pow_jump);
                    pow_S++;
                }
                if (Pr_W_given_that_H < Math.pow(10, -pow_jump)) {
                    Pr_W_given_that_H = Pr_W_given_that_H * Math.pow(10, pow_jump);
                    pow_H++;
                }
            }
        }
        // shorten the powers so that the doubles don't get too small
        if (pow_H > pow_S) {
            pow_H = pow_H - pow_S;
            pow_S = 0;
        } else if (pow_H < pow_S) {
            pow_S = pow_S - pow_H;
            pow_H = 0;
        } else {
            pow_S = 0;
            pow_H = 0;
        }
        // multiply the doubles again
        // this time with the negative power
        Pr_W_given_that_S = Pr_W_given_that_S * Math.pow(10, -pow_S * pow_jump);
        Pr_W_given_that_H = Pr_W_given_that_H * Math.pow(10, -pow_H * pow_jump);

        // probability of Spam given the words in the mail
        return Pr_W_given_that_S / (Pr_W_given_that_S + Pr_W_given_that_H);
    }
}