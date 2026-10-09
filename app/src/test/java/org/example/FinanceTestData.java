package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class FinanceTestData {
    static ObjectNode empty() throws Exception {
        return (ObjectNode) new ObjectMapper().readTree("""
            {"debts":[],"flows":[],"accounts":[],"subs":[],"incomes":[],"goals":[],
             "settings":{"ccTiers":[{"max":30000,"akdi":3.25,"gecikme":3.55},
               {"max":180000,"akdi":3.75,"gecikme":4.05},{"max":null,"akdi":4.25,"gecikme":4.55}],
               "nakitAvans":{"akdi":4.25,"gecikme":4.55},"kurumsal":{"akdi":4.25,"gecikme":4.55},
               "asgariEsik":50000,"asgariAlt":20,"asgariUst":40,"vergi":30,"gunlukButce":0}}
            """);
    }

    static ObjectNode populated() throws Exception {
        ObjectNode root = empty();
        root.withArray("debts").addObject().put("id", "card").put("name", "Örnek kart")
                .put("type", "kredikarti").put("limit", 50000).put("balance", 1000).put("due", "2026-10-31");
        root.withArray("debts").addObject().put("id", "receivable").put("name", "Arkadaştan alacak")
                .put("type", "elden").put("yon", "alacak").put("balance", 600).put("due", "2026-10-10");
        root.withArray("debts").addObject().put("id", "loan").put("name", "Bilgisayar taksiti")
                .put("type", "kredi").put("principal", 3000).put("rate", 0).put("vade", 3)
                .put("kalanTaksit", "3").put("taksit", "1000").put("due", "2026-10-31");
        root.withArray("accounts").addObject().put("id", "bank").put("name", "Günlük hesap")
                .put("kind", "banka").put("balance", 5000).put("kisitli", 500);
        root.withArray("subs").addObject().put("id", "internet").put("name", "İnternet")
                .put("amount", 200).put("day", 15).put("active", true);
        root.withArray("incomes").addObject().put("id", "salary").put("name", "Beklenen gelir")
                .put("amount", 8000).put("date", "2026-10-10").put("repeat", "aylik").put("kind", "serbest");
        root.withArray("goals").addObject().put("id", "goal").put("name", "Ekipman birikimi")
                .put("amount", 10000).put("saved", 500).put("priority", 2).put("due", "2026-12-31");
        return root;
    }
}
