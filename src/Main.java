import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static Map<String, Double> ham_w_occurrences;
    private static Map<String, Double> spam_w_occurrences;

    public static void main(String[] args) throws IOException {
        System.out.println("Hello world!");
        String DEFAULT_PATH = "/home/obiwan/Documents/dist/";

        List<File> ham_anl_mails = getMails(Paths.get(DEFAULT_PATH, "ham-anlern"));
        List<File> spam_anl_mails = getMails(Paths.get(DEFAULT_PATH, "spam-anlern"));
        List<File> spam_kall_mails = getMails(Paths.get(DEFAULT_PATH, "spam-kallibrierung"));
        List<File> ham_kall_mails = getMails(Paths.get(DEFAULT_PATH, "ham-kallibrierung"));

        ham_w_occurrences = wordOccurrenceCounter(ham_anl_mails);
        spam_w_occurrences = wordOccurrenceCounter(spam_anl_mails);

        //Union of keys from both maps
        Set<String> unionHamSpam = new HashSet<>(ham_w_occurrences.keySet());
        unionHamSpam.addAll(spam_w_occurrences.keySet());

        Set<String> spam_only_words = new HashSet<>(unionHamSpam);
        spam_only_words.removeAll(ham_w_occurrences.keySet());

        Set<String> ham_only_words = new HashSet<>(unionHamSpam);
        ham_only_words.removeAll(spam_w_occurrences.keySet());
        ham_only_words.forEach(s -> spam_w_occurrences.put(s, 0.3));
        spam_only_words.forEach(s -> ham_w_occurrences.put(s, 0.3));

        System.out.println("Total spam mails: " + spam_w_occurrences.values().stream().mapToInt(Double::intValue).sum());
        System.out.println("Total ham mails: " + ham_w_occurrences.values().stream().mapToInt(Double::intValue).sum());
        for (File spam_kall_mail:
             spam_kall_mails) {
            List<String> kall_mail_words = mailWords(spam_kall_mail);
            double mail_spaminess = spaminess(kall_mail_words);
            System.out.println(mail_spaminess*100 + "%");
        }
    }

    static List<File> getMails(Path path) throws IOException {
        return Files.walk(path)
                .filter(Files::isRegularFile)
                .map(Path::toFile).toList();
    }

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

    static double spaminess(List<String> words) {
        double pWnS = 1;
        double pWnH = 1;
        int i = 0;
        double spam_words_sum = spam_w_occurrences.values().stream().mapToInt(Double::intValue).sum();
        double ham_words_sum = ham_w_occurrences.values().stream().mapToInt(Double::intValue).sum();
        int powS, powH;
        powS = powH = 0;
        for (String word :
                words) {
            if (spam_w_occurrences.containsKey(word) && ham_w_occurrences.containsKey(word)) {
                pWnS = pWnS * (spam_w_occurrences.get(word) / spam_words_sum);
                pWnH = pWnH * (ham_w_occurrences.get(word) / ham_words_sum);

                //System.out.println(pWnS);
                //System.out.println(pWnH);
                if (pWnS < Math.pow(10, -100)) {
                    //System.out.println("getting too big...");
                    pWnS = pWnS * Math.pow(10, 100);
                    powS++;
                    //System.out.println(pWnS);
                    //System.out.println(pWnH);
                    //System.out.println("continuing");
                }
                if (pWnH < Math.pow(10, -100)){
                    pWnH = pWnH * Math.pow(10, 100);
                    powH++;
                }
                i++;
                //System.out.println(i);
            }
        }
        //System.out.println(pWnS);
        //System.out.println(pWnH);
        if (powH > powS) {
            powH = powH - powS;
            powS = 0;
        }
        else if (powH < powS){
            powS = powS - powH;
            powH = 0;
        }
        else powS = powH = 0;
        return pWnS * Math.pow(10,powS) / (pWnS * Math.pow(10,powS) + pWnH * Math.pow(10,powH)); // pSnW
    }

}