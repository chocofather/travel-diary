package com.example.travlediary.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class LoginThrottle {

    static final int IP_FAILURE_LIMIT = 20;
    static final Duration IP_FAILURE_WINDOW = Duration.ofMinutes(1);
    static final Duration IP_BLOCK_DURATION = Duration.ofMinutes(5);

    private static final Duration DEFAULT_RETENTION = Duration.ofHours(24);
    private static final int DEFAULT_ACCOUNT_CAPACITY = 50_000;
    private static final int DEFAULT_IP_CAPACITY = 20_000;
    private static final int CLEANUP_INTERVAL = 256;
    private static final int MAX_ACCOUNT_KEY_LENGTH = 128;
    private static final int MAX_IP_KEY_LENGTH = 64;

    private final Clock clock;
    private final Duration retention;
    private final int accountCapacity;
    private final int ipCapacity;
    private final LinkedHashMap<String, AccountState> accounts = accessOrderedMap();
    private final LinkedHashMap<String, IpState> ipAddresses = accessOrderedMap();
    private int operationsSinceCleanup;

    public LoginThrottle() {
        this(Clock.systemUTC(), DEFAULT_RETENTION,
                DEFAULT_ACCOUNT_CAPACITY, DEFAULT_IP_CAPACITY);
    }

    LoginThrottle(Clock clock,
                  Duration retention,
                  int accountCapacity,
                  int ipCapacity) {
        this.clock = clock;
        this.retention = retention;
        this.accountCapacity = accountCapacity;
        this.ipCapacity = ipCapacity;
    }

    public synchronized boolean isBlocked(String username, String ipAddress) {
        return status(username, ipAddress).blocked();
    }

    public synchronized LoginThrottleStatus status(String username, String ipAddress) {
        Instant now = clock.instant();
        AccountState account = currentAccount(normalizeAccount(username), now);
        IpState ip = currentIp(normalizeIp(ipAddress), now);
        cleanUpIfNeeded(now);
        return status(account, ip, now);
    }

    public synchronized LoginThrottleStatus ipStatus(String ipAddress) {
        Instant now = clock.instant();
        IpState ip = currentIp(normalizeIp(ipAddress), now);
        cleanUpIfNeeded(now);
        Instant blockedUntil = ip == null || !isActive(ip.blockedUntil, now)
                ? null
                : ip.blockedUntil;
        return new LoginThrottleStatus(0, blockedUntil);
    }

    public synchronized LoginThrottleStatus recordFailure(String username, String ipAddress) {
        Instant now = clock.instant();

        String accountKey = normalizeAccount(username);
        AccountState account = currentAccount(accountKey, now);
        if (account == null) {
            account = new AccountState();
            accounts.put(accountKey, account);
        }
        account.failures++;
        account.lastSeen = now;
        Duration accountDelay = accountDelay(account.failures);
        if (!accountDelay.isZero()) {
            account.blockedUntil = now.plus(accountDelay);
        }

        String ipKey = normalizeIp(ipAddress);
        IpState ip = currentIp(ipKey, now);
        if (ip == null) {
            ip = new IpState();
            ipAddresses.put(ipKey, ip);
        }
        Instant windowStart = now.minus(IP_FAILURE_WINDOW);
        while (!ip.failures.isEmpty()
                && !ip.failures.peekFirst().isAfter(windowStart)) {
            ip.failures.removeFirst();
        }
        ip.failures.addLast(now);
        while (ip.failures.size() > IP_FAILURE_LIMIT) {
            ip.failures.removeFirst();
        }
        ip.lastSeen = now;
        if (ip.failures.size() >= IP_FAILURE_LIMIT) {
            ip.blockedUntil = now.plus(IP_BLOCK_DURATION);
        }

        trimToCapacity(accounts, accountCapacity);
        trimToCapacity(ipAddresses, ipCapacity);
        cleanUpIfNeeded(now);
        return status(account, ip, now);
    }

    public synchronized void recordSuccess(String username) {
        accounts.remove(normalizeAccount(username));
        cleanUpIfNeeded(clock.instant());
    }

    private AccountState currentAccount(String key, Instant now) {
        AccountState state = accounts.get(key);
        if (state != null && isExpired(state.lastSeen, now)) {
            accounts.remove(key);
            return null;
        }
        return state;
    }

    private IpState currentIp(String key, Instant now) {
        IpState state = ipAddresses.get(key);
        if (state != null && isExpired(state.lastSeen, now)) {
            ipAddresses.remove(key);
            return null;
        }
        return state;
    }

    private boolean isExpired(Instant lastSeen, Instant now) {
        return lastSeen != null && !lastSeen.plus(retention).isAfter(now);
    }

    private boolean isActive(Instant blockedUntil, Instant now) {
        return blockedUntil != null && now.isBefore(blockedUntil);
    }

    private LoginThrottleStatus status(AccountState account, IpState ip, Instant now) {
        Instant accountBlock = account == null || !isActive(account.blockedUntil, now)
                ? null
                : account.blockedUntil;
        Instant ipBlock = ip == null || !isActive(ip.blockedUntil, now)
                ? null
                : ip.blockedUntil;
        Instant blockedUntil = later(accountBlock, ipBlock);
        return new LoginThrottleStatus(
                account == null ? 0 : account.failures,
                blockedUntil);
    }

    private Instant later(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null || first.isAfter(second)) {
            return first;
        }
        return second;
    }

    private Duration accountDelay(int failures) {
        return switch (failures) {
            case 5 -> Duration.ofSeconds(10);
            case 6 -> Duration.ofSeconds(30);
            case 7 -> Duration.ofMinutes(1);
            default -> failures >= 8 ? Duration.ofMinutes(5) : Duration.ZERO;
        };
    }

    private void cleanUpIfNeeded(Instant now) {
        operationsSinceCleanup++;
        if (operationsSinceCleanup < CLEANUP_INTERVAL) {
            return;
        }
        operationsSinceCleanup = 0;
        removeExpired(accounts, now);
        removeExpired(ipAddresses, now);
    }

    private <T extends SeenState> void removeExpired(Map<String, T> states, Instant now) {
        states.entrySet().removeIf(entry -> isExpired(entry.getValue().lastSeen, now));
    }

    private <T> void trimToCapacity(LinkedHashMap<String, T> states, int capacity) {
        Iterator<String> iterator = states.keySet().iterator();
        while (states.size() > capacity && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private String normalizeAccount(String username) {
        return normalize(username, "<empty>", MAX_ACCOUNT_KEY_LENGTH)
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String ipAddress) {
        return normalize(ipAddress, "<unknown>", MAX_IP_KEY_LENGTH);
    }

    private String normalize(String value, String emptyValue, int maxLength) {
        if (value == null || value.isBlank()) {
            return emptyValue;
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private static <K, V> LinkedHashMap<K, V> accessOrderedMap() {
        return new LinkedHashMap<>(16, 0.75f, true);
    }

    private abstract static class SeenState {
        Instant lastSeen;
    }

    private static final class AccountState extends SeenState {
        private int failures;
        private Instant blockedUntil;
    }

    private static final class IpState extends SeenState {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockedUntil;
    }
}
