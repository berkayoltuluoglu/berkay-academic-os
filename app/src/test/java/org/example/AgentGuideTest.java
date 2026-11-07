package org.example;

import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.util.regex.*;import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class AgentGuideTest {
    @TempDir Path root;
    @Test void documentedJsonExampleValidatesAndPackagedGuideMatchesSource()throws Exception{
        String guide=Files.readString(Path.of("../AGENTS.md"));Matcher match=Pattern.compile("```json\\n(.*?)\\n```",Pattern.DOTALL).matcher(guide);assertTrue(match.find());
        Path proposal=root.resolve("example.json");Files.writeString(proposal,match.group(1));var out=new ByteArrayOutputStream();assertEquals(0,AgentCli.run(new String[]{"--profile",root.toString(),"validate",proposal.toString()},new PrintStream(out)),out.toString());
        assertTrue(PlanningFiles.JSON.readTree(out.toString()).path("stale").asBoolean());
        try(var stream=AgentGuide.class.getResourceAsStream("/agent-guide/AGENTS.md")){assertNotNull(stream);assertEquals(guide,new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        AgentGuide.install(root);assertEquals(guide,Files.readString(root.resolve("AGENTS.md")));Files.writeString(root.resolve("AGENTS.md"),"Özel kullanıcı kılavuzu");AgentGuide.install(root);assertEquals("Özel kullanıcı kılavuzu",Files.readString(root.resolve("AGENTS.md")));
    }
    @Test void generatedLauncherRunsWithoutDisplayAndUsesAnIsolatedProfile()throws Exception{
        var javaCommand=Path.of(System.getProperty("java.home"),"bin","java").toString();var builder=new ProcessBuilder(javaCommand,"-cp",Path.of("build/install/app/lib/*").toAbsolutePath().toString(),"org.example.AgentCli","--profile",root.toString(),"snapshot","--week","2026-11-09");builder.environment().remove("DISPLAY");var process=builder.start();String result=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue(),new String(process.getErrorStream().readAllBytes()));assertEquals("Europe/Istanbul",PlanningFiles.JSON.readTree(result).path("timezone").asText());
    }
}
