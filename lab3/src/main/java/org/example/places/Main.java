package org.example.places;

import org.example.places.model.*;
import org.example.places.utils.ApplicationService;

import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.print("Введите название локации: ");
        String query = scanner.nextLine();

        List<Location> locations =
                ApplicationService.findLocations(query).join();

        if (locations.isEmpty()) {
            System.out.println("Ничего не найдено");
            return;
        }

        for (int i = 0; i < locations.size(); i++) {
            System.out.println(i + ": " + locations.get(i).name());
        }

        System.out.print("Выберите номер: ");
        Location chosen = locations.get(scanner.nextInt());

        Result result =
                ApplicationService.buildResult(chosen).join();

        System.out.println("\n=== ПОГОДА ===");
        System.out.println(result.weather());

        System.out.println("\n=== ИНТЕРЕСНЫЕ МЕСТА ===");
        for (Place place : result.places()) {
            System.out.println("\n" + place.title());
            System.out.println(place.description());
        }
    }
}