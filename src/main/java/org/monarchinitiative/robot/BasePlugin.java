package org.monarchinitiative.robot;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.obolibrary.robot.Command;
import org.obolibrary.robot.CommandLineHelper;
import org.obolibrary.robot.CommandState;

/**
 * Helper base class for ROBOT commands.
 * 
 * This class is intended to serve as a base class for ROBOT commands, to avoid
 * duplicating boilerplate across several commands. Subclasses should call the
 * constructor with the desired name, description, and help message, add any
 * option they need, and implement the {@link performOperation} method.
 */
public abstract class BasePlugin implements Command {

    private String name;
    private String description;
    private String usage;
    private boolean with_io;
    protected Options options;

    /**
     * Creates a new command.
     * 
     * @param name        The command name, as it should be invoked on the command
     *                    line.
     * @param description The description of the command that ROBOT will display.
     * @param usage       The help message for the command.
     * @param with_io     If true, input/output options will be handled
     *                    automatically.
     */
    protected BasePlugin(String name, String description, String usage, boolean with_io) {
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.with_io = with_io;
        options = CommandLineHelper.getCommonOptions();
        if ( with_io ) {
            options.addOption("i", "input", true, "load ontology from file");
            options.addOption("I", "input-iri", true, "load ontology from IRI");
            options.addOption("o", "output", true, "save ontology to file");
        }
    }

    /**
     * Creates a new command with default I/O options.
     * 
     * @param name        The command name, as it should be involed on the command
     *                    line.
     * @param description The description of the command that ROBOT will display.
     * @param usage       The help message for the command.
     */
    protected BasePlugin(String name, String description, String usage) {
        this(name, description, usage, true);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usage;
    }

    public Options getOptions() {
        return options;
    }

    public void main(String[] args) {
        try {
            execute(null, args);
        } catch ( Exception e ) {
            CommandLineHelper.handleException(e);
        }
    }

    public CommandState execute(CommandState state, String[] args) throws Exception {
        CommandLine line = CommandLineHelper.getCommandLine(usage, options, args);
        if ( line == null ) {
            return null;
        }

        if ( state == null ) {
            state = new CommandState();
        }

        if ( with_io ) {
            state = CommandLineHelper.updateInputOntology(CommandLineHelper.getIOHelper(line), state, line);
        }
        performOperation(state, line);
        if ( with_io ) {
            CommandLineHelper.maybeSaveOutput(line, state.getOntology());
        }

        return state;
    }

    /**
     * Perform whatever operation the command is supposed to do.
     * 
     * @param state The internal state of ROBOT.
     * @param line  The command line used to invoke the command.
     * @throws Exception If any error occurred when attempting to execute the
     *                   operation.
     */
    public abstract void performOperation(CommandState state, CommandLine line) throws Exception;

}
