package de.wthack.mcp.steam;

import com.google.common.base.Suppliers;
import com.lukaspradel.steamapi.core.exception.SteamApiException;
import com.lukaspradel.steamapi.data.json.friendslist.Friend;
import com.lukaspradel.steamapi.data.json.friendslist.GetFriendList;
import com.lukaspradel.steamapi.data.json.ownedgames.Game;
import com.lukaspradel.steamapi.data.json.ownedgames.GetOwnedGames;
import com.lukaspradel.steamapi.data.json.playersummaries.GetPlayerSummaries;
import com.lukaspradel.steamapi.webapi.client.SteamWebApiClient;
import com.lukaspradel.steamapi.webapi.request.GetFriendListRequest;
import com.lukaspradel.steamapi.webapi.request.GetOwnedGamesRequest;
import com.lukaspradel.steamapi.webapi.request.builders.SteamWebApiRequestFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SteamService {
    @Value("${my_steam_id}")
    private String mySteamId;
    @Value("${my_steam_key}")
    private String mySteamKey;
    private final Supplier<SteamWebApiClient> steamWebApiClientSupplier = Suppliers.memoize(() -> (new SteamWebApiClient.SteamWebApiClientBuilder(mySteamKey)).build());

    @Tool(description = "List friends for steam id. Includes names, online states and steam ids.", resultConverter = JSONConverter.class)
    public String listSteamFriends(String steamId) throws SteamApiException {
        return steamWebApiClientSupplier.get()
                .<GetFriendList>processRequest(new GetFriendListRequest.GetFriendListRequestBuilder(steamId).buildRequest())
                .getFriendslist()
                .getFriends()
                .stream()
                .map(Friend::getSteamid)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Stream::of))
                .map(SteamWebApiRequestFactory::createGetPlayerSummariesRequest)
                .flatMap(r -> {
                    try {
                        return steamWebApiClientSupplier.get().<GetPlayerSummaries>processRequest(r).getResponse().getPlayers().stream();
                    } catch (SteamApiException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(p -> {
                    JSONObject obj = new JSONObject();
                    obj.put("steam_id", p.getSteamid());
                    obj.put("name", p.getPersonaname());
                    String state = switch (p.getPersonastate().intValue()) {
                        case 0 -> "OFFLINE";
                        case 1 -> "ONLINE";
                        case 2 -> "BUSY";
                        case 3 -> "AWAY";
                        case 4 -> "SNOOZE";
                        case 5 -> "LOOKING FOR TRADE";
                        case 6 -> "LOOKING FOR PLAY";
                        default -> "UNKNOWN";
                    };
                    obj.put("state", state);
                    return obj;
                }).collect(JSONArray::new, JSONArray::put, (a, b) -> a.putAll(b.toList())).toString();
    }

    @Tool(description = "Get my steam id.")
    public String getMySteamId() {
        return mySteamId;
    }

    public static class JSONConverter implements ToolCallResultConverter {
        @Override
        public String convert(@Nullable Object result, @Nullable Type returnType) {
            if (result instanceof JSONArray) {
                return result.toString();
            } else {
                return (new DefaultToolCallResultConverter()).convert(result, returnType);
            }
        }
    }

    @Tool(description = "List all steam games for steam id.", resultConverter = JSONConverter.class)
    public JSONArray listSteamGames(String streamId) throws SteamApiException {
        return steamWebApiClientSupplier.get()
                .<GetOwnedGames>processRequest(new GetOwnedGamesRequest.GetOwnedGamesRequestBuilder(streamId).includeAppInfo(true).buildRequest())
                .getResponse()
                .getGames()
                .stream()
                .sorted(Comparator.comparingLong(Game::getPlaytimeForever).reversed())
                .limit(100)
                .map(g -> {
                    JSONObject obj = new JSONObject();
                    obj.put("name", g.getName());
                    obj.put("playtime_minutes", g.getPlaytimeForever());
                    return obj.toString();
                }).collect(JSONArray::new, JSONArray::put, (a, b) -> a.putAll(b.toList()));
    }
}
