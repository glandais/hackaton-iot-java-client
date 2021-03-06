package com.capgemini.csd.hackaton.client;

import io.airlift.airline.Cli;
import io.airlift.airline.Cli.CliBuilder;
import io.airlift.airline.Help;

public class Main {

	public static void main(String[] args) {
		CliBuilder<Runnable> builder = Cli.<Runnable> builder("hackaton-client").withDefaultCommand(Help.class)
				.withCommands(Help.class, ExecutionClient.class);

		Cli<Runnable> parser = builder.build();
		parser.parse(args).run();
	}

}
