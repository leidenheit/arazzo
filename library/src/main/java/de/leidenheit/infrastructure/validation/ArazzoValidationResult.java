package de.leidenheit.infrastructure.validation;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ArazzoValidationResult {

    private boolean invalid;
    private final Map<Location, String> invalidTypeMap = new LinkedHashMap<>();
    private final List<Location> missingList = new ArrayList<>();
    private final List<Location> warningList = new ArrayList<>();
    private final List<Location> uniqueList = new ArrayList<>();
    private final List<Location> reservedList = new ArrayList<>();

    public void addOtherValidationResult(final ArazzoValidationResult otherResult) {
        if (otherResult.invalid) {
            setInvalid(true);
        }
        this.invalidTypeMap.putAll(otherResult.invalidTypeMap);
        this.missingList.addAll(otherResult.missingList);
        this.warningList.addAll(otherResult.warningList);
        this.uniqueList.addAll(otherResult.uniqueList);
        this.reservedList.addAll(otherResult.reservedList);
    }

    public void addInvalidType(final String location, final String key, final String expectedType) {
        invalidTypeMap.put(new Location(location, key), expectedType);
        setInvalid(true);
    }

    public void addMissing(final String location, final String key) {
        missingList.add(new Location(location, key));
        setInvalid(true);
    }

    public void addWarning(final String location, final String key) {
        warningList.add(new Location(location, key));
    }

    public void addUnique(final String location, final String key) {
        uniqueList.add(new Location(location, key));
        setInvalid(true);
    }

    public void addReserved(final String location, final String key) {
        reservedList.add(new Location(location, key));
        setInvalid(true);
    }

    public List<String> getMessages() {
        List<String> messages = new ArrayList<>();

        for (Map.Entry<Location, String> entry : invalidTypeMap.entrySet()) {
            var l = entry.getKey();
            String location = l.location.isEmpty() ? "" : l.location + ".";
            String message = "Attribute " + location + l.key + " is not of type `" + invalidTypeMap.get(l) + "`";
            messages.add(message);
        }
        for (Location l : missingList) {
            String location = l.location.isEmpty() ? "" : l.location + ".";
            String message = "Attribute " + location + l.key + " is missing";
            messages.add(message);
        }
        for (Location l : warningList) {
            String location = l.location.isEmpty() ? "" : l.location + ".";
            String message = location + l.key;
            messages.add(message);
        }
        for (Location l : uniqueList) {
            String location = l.location.isEmpty() ? "" : l.location + ".";
            String message = "Attribute " + location + l.key + " is repeated";
            messages.add(message);
        }
        for (Location l : reservedList) {
            String location = l.location.isEmpty() ? "" : l.location + ".";
            String message = "Attribute " + location + l.key + " is reserved by the Arazzo Specification";
            messages.add(message);
        }
        return messages;
    }

    protected record Location(String location, String key) {}
}
