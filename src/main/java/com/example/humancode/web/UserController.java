package com.example.humancode.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.humancode.user.LeaderboardEntry;
import com.example.humancode.user.User;
import com.example.humancode.user.UserProfile;
import com.example.humancode.user.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Handles and the board.
 *
 * <p>Nothing here can change a rating. {@code POST /sessions/{id}/finish} is
 * the only route that moves one, from a report the server wrote — see
 * {@link UserService}. Keep it that way: an endpoint on this controller that
 * accepted a number would make the whole board decorative.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService users;

    /** Claims a handle. 409 if somebody already has it, 400 if it is not one. */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.ClaimUserResponse claim(@RequestBody Dtos.ClaimUserRequest request) {
        User user = users.claim(request == null ? null : request.handle());
        return new Dtos.ClaimUserResponse(users.profile(user), user.getToken());
    }

    /**
     * Signs a browser back in with the token it kept. 403 when the handle is
     * unknown or the token is wrong — the same answer either way, so this is
     * not a way to find out which handles exist.
     */
    @PostMapping("/users/resume")
    public UserProfile resume(@RequestBody Dtos.ResumeUserRequest request) {
        return users.profile(users.resume(request.handle(), request.token()));
    }

    /** Anyone's standing, by handle. Public — it is a leaderboard. */
    @GetMapping("/users/{handle}")
    public UserProfile profile(@PathVariable String handle) {
        return users.profileFor(handle)
                .orElseThrow(() -> new UserService.UnknownUserException(handle));
    }

    /**
     * @param handle the viewer, so their own row is marked and their standing
     *               comes back even when they are below the cut. Optional, and
     *               unauthenticated on purpose: it reveals nothing that
     *               {@code GET /users/{handle}} does not already.
     */
    @GetMapping("/leaderboard")
    public Dtos.LeaderboardResponse leaderboard(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String handle) {
        List<LeaderboardEntry> entries = users.leaderboard(limit, handle);
        UserProfile you = handle == null || handle.isBlank()
                ? null
                : users.profileFor(handle).orElse(null);
        return new Dtos.LeaderboardResponse(entries, users.boardSize(), you);
    }
}
