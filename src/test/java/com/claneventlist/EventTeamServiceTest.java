package com.claneventlist;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * Team roster parsing. Staff paste rosters with comments and Windows line endings;
 * a stray token here becomes a phantom team member.
 */
public class EventTeamServiceTest
{
    @Test
    public void splitNamesStripsCommentsLineEndingsAndBlanks()
    {
        String text = "Alice, Bob # captains\r\n"
            + "// whole line comment\r\n"
            + "Carol // sub\r\n"
            + " , ,\n"
            + "\r\n"
            + "Dave,Eve\n";

        assertEquals(
            Arrays.asList("Alice", "Bob", "Carol", "Dave", "Eve"),
            EventTeamService.splitNames(text));
    }

    @Test
    public void splitNamesHandlesEmptyInput()
    {
        assertEquals(Collections.emptyList(), EventTeamService.splitNames(null));
        assertEquals(Collections.emptyList(), EventTeamService.splitNames(""));
    }
}
