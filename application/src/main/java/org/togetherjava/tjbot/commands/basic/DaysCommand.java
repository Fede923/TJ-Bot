package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class DaysCommand extends SlashCommandAdapter {
    private static final String FROM_OPTION = "from";
    private static final String TO_OPTION = "to";
    private static final String FORMAT = "dd.MM.yyyy";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(FORMAT);

    public DaysCommand(){
        super("days", "Computes the difference in days between given dates", CommandVisibility.GUILD);
        getData().addOption(OptionType.STRING,"from","the start date, in the format 'dd.MM.yyyy'",true);
        getData().addOption(OptionType.STRING,"to","the end date, in the format 'dd.MM.yyyy'",true);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String from = Objects.requireNonNull(event.getOption(FROM_OPTION)).getAsString();
        String to = Objects.requireNonNull(event.getOption(TO_OPTION)).getAsString();
        LocalDate fromDate;
        LocalDate toDate;

        try {
             fromDate = LocalDate.parse(from, FORMATTER);
             toDate = LocalDate.parse(to, FORMATTER);
        } catch (DateTimeParseException e) {
            event.reply("The dates must be in the format 'dd.MM.yyyy', try again.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        long days = ChronoUnit.DAYS.between(fromDate, toDate);
        event.reply("The difference between %s and %s are %d days".formatted(from, to, days))
                .queue();
    }
}
