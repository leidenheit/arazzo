package de.leidenheit;

import org.springframework.stereotype.Service;

@Service
public class DummyService {
    public String sayHello() {
        return "Hello world from library!";
    }
}