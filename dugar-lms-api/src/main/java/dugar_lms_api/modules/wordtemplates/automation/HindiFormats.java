package dugar_lms_api.modules.wordtemplates.automation;

import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class HindiFormats {
    private HindiFormats() {}
    private static final String[] N=("शून्य एक दो तीन चार पाँच छह सात आठ नौ दस ग्यारह बारह तेरह चौदह पंद्रह सोलह सत्रह अठारह उन्नीस बीस इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस तीस इकतीस बत्तीस तैंतीस चौंतीस पैंतीस छत्तीस सैंतीस अड़तीस उनतालीस चालीस इकतालीस बयालीस तैंतालीस चवालीस पैंतालीस छियालीस सैंतालीस अड़तालीस उनचास पचास इक्यावन बावन तिरपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ साठ इकसठ बासठ तिरसठ चौंसठ पैंसठ छियासठ सड़सठ अड़सठ उनहत्तर सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर अठहत्तर उनासी अस्सी इक्यासी बयासी तिरासी चौरासी पचासी छियासी सत्तासी अट्ठासी नवासी नब्बे इक्यानवे बानवे तिरानवे चौरानवे पंचानवे छियानवे सत्तानवे अट्ठानवे निन्यानवे").split(" ");
    public static String number(long value) {
        if(value<0)return "ऋण "+number(Math.negateExact(value));
        if(value<100)return N[(int)value];
        long[] scale={10000000,100000,1000,100};String[] unit={"करोड़","लाख","हज़ार","सौ"};
        for(int i=0;i<scale.length;i++)if(value>=scale[i])return number(value/scale[i])+" "+unit[i]+(value%scale[i]==0?"":" "+number(value%scale[i]));
        throw new IllegalArgumentException("Unsupported amount");
    }
    public static String format(String kind,String value) {
        if(value.isBlank())return value;
        if("DATE".equals(kind)) {
            LocalDate date;
            try {date=LocalDate.parse(value);}catch(Exception e){try{date=LocalDate.parse(value,DateTimeFormatter.ofPattern("dd/MM/yyyy"));}catch(Exception invalid){throw new IllegalArgumentException("Date formatter requires an ISO or dd/MM/yyyy source value");}}
            String[] months={"जनवरी","फ़रवरी","मार्च","अप्रैल","मई","जून","जुलाई","अगस्त","सितंबर","अक्टूबर","नवंबर","दिसंबर"};
            return date.getDayOfMonth()+" "+months[date.getMonthValue()-1]+" "+date.getYear();
        }
        BigDecimal amount;
        try {amount=new BigDecimal(value.replace(",",""));}catch(Exception e){throw new IllegalArgumentException("Amount formatter requires a numeric source value");}
        if("CURRENCY".equals(kind)) {
            String[] parts=amount.abs().setScale(2,RoundingMode.HALF_UP).toPlainString().split("\\.");String integer=parts[0];
            if(integer.length()>3){String head=integer.substring(0,integer.length()-3);integer=head.replaceAll("\\B(?=(\\d{2})+(?!\\d))",",")+","+integer.substring(integer.length()-3);}
            return (amount.signum()<0?"-":"")+"₹"+integer+"."+parts[1];
        }
        if(!"AMOUNT_WORDS".equals(kind))throw new IllegalArgumentException("Unknown deterministic format");
        BigDecimal rounded=amount.setScale(2,RoundingMode.HALF_UP);long rupees;
        // Split after rounding so 99.995 correctly becomes one hundred rupees.
        rupees=rounded.setScale(0,RoundingMode.DOWN).longValueExact();
        int paise=rounded.remainder(BigDecimal.ONE).abs().movePointRight(2).intValueExact();
        return number(rupees)+" रुपये"+(paise==0?"":" और "+number(paise)+" पैसे")+" मात्र";
    }
}
