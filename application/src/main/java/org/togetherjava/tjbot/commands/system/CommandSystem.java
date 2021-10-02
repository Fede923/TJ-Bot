package org.togetherjava.tjbot.commands.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.CommandRegistry;
import org.togetherjava.tjbot.commands.SlashCommand;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.util.TriConsumer;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CommandSystem extends ListenerAdapter implements SlashCommandProvider {
    private static final Logger logger = LoggerFactory.getLogger(CommandSystem.class);

    private final Map<String, SlashCommand> nameToSlashCommands;
    private static final String RELOAD_COMMAND = "reload";

    @SuppressWarnings("ThisEscapedInObjectConstruction")
    public CommandSystem(@NotNull Database database) {
        nameToSlashCommands = CommandRegistry.createSlashCommands(database)
            .stream()
            .collect(Collectors.toMap(SlashCommand::getName, Function.identity()));

        if (nameToSlashCommands.containsKey(RELOAD_COMMAND)) {
            throw new IllegalStateException(
                    "The 'reload' command is a special reserved command that must not be used by other commands");
        }
        nameToSlashCommands.put(RELOAD_COMMAND, new ReloadCommand(this));

        if (logger.isInfoEnabled()) {
            logger.info("Available commands: {}", nameToSlashCommands.values());
        }
    }

    @Override
    public @NotNull Collection<SlashCommand> getSlashCommands() {
        return Collections.unmodifiableCollection(nameToSlashCommands.values());
    }

    @Override
    public @NotNull Optional<SlashCommand> getSlashCommand(@NotNull String name) {
        return Optional.ofNullable(nameToSlashCommands.get(name));
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        // Register reload on all guilds
        event.getJDA().getGuildCache().forEach(this::registerReloadCommand);
    }

    private void registerReloadCommand(@NotNull Guild guild) {
        guild.retrieveCommands().queue(commands -> {
            // Has it been registered already?
            if (commands.stream()
                .map(net.dv8tion.jda.api.interactions.commands.Command::getName)
                .anyMatch(RELOAD_COMMAND::equals)) {
                return;
            }

            logger.debug("Register '{}' for guild '{}'", RELOAD_COMMAND, guild.getName());
            SlashCommand reloadCommand = requireSlashCommand(RELOAD_COMMAND);
            guild.upsertCommand(reloadCommand.getData()).queue();
        });
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        logger.debug("Received slash command '{}' (#{}) on guild '{}'", event.getName(),
                event.getId(), event.getGuild());
        requireSlashCommand(event.getName()).onSlashCommand(event);
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event) {
        logger.debug("Received button click '{}' (#{}) on guild '{}'", event.getComponentId(),
                event.getId(), event.getGuild());
        forwardComponentCommand(event, SlashCommand::onButtonClick);
    }

    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event) {
        logger.debug("Received selection menu event '{}' (#{}) on guild '{}'",
                event.getComponentId(), event.getId(), event.getGuild());
        forwardComponentCommand(event, SlashCommand::onSelectionMenu);
    }

    private <T extends ComponentInteraction> void forwardComponentCommand(@NotNull T event,
            @NotNull TriConsumer<? super SlashCommand, ? super T, ? super List<String>> commandArgumentConsumer) {
        ComponentId componentId;
        try {
            componentId = ComponentIds.parse(event.getComponentId());
        } catch (JsonProcessingException e) {
            logger
                .error("Unable to route event (#{}) back to its corresponding slash command. The component ID was in an unexpected format."
                        + " All button and menu events have to use a component ID created in a specific format"
                        + " (refer to the documentation of SlashCommand). Component ID was: {}",
                        event.getId(), event.getComponentId(), e);
            // Unable to forward, simply fade out the event
            return;
        }
        SlashCommand command = requireSlashCommand(componentId.getCommandName());
        commandArgumentConsumer.accept(command, event, componentId.getElements());
    }

    private @NotNull SlashCommand requireSlashCommand(@NotNull String name) {
        return Objects.requireNonNull(nameToSlashCommands.get(name));
    }
}