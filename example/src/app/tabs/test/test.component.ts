import {Component, OnInit} from '@angular/core';
import {IonButton, IonContent, IonItem, IonLabel, IonList} from "@ionic/angular/standalone";
import {HeaderComponent} from "../../components/header/header.component";
import {CapacitorCalendar, EventSpan, RecurrenceFrequency} from "@ebarooni/capacitor-calendar";
import {StoreService} from "../../store/store.service";

@Component({
  selector: 'app-test',
  templateUrl: './test.component.html',
  styleUrls: ['./test.component.scss'],
  imports: [HeaderComponent, IonContent, IonItem, IonList, IonLabel, IonButton],
  standalone: true
})
export class TestComponent implements OnInit {

  private lastCreatedRecurringEvent: string = "";
  private lastCreatedEvent: string = "";
  private lastCreatedCalendar: string = "";

  constructor(
    private readonly storeService: StoreService,
  ) {
  }

  ngOnInit() {
  }

  requestAllPermissions() {
    CapacitorCalendar.requestAllPermissions().then((response) =>
      this.storeService.dispatchLog(JSON.stringify(response)),
    ).catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
  }

  listEventsInRange() {
    const now = Date.now();
    const oneWeek = 604800000
    CapacitorCalendar.listEventsInRange({startDate: now - oneWeek, endDate: now + oneWeek}).then((response) =>
      this.storeService.dispatchLog(JSON.stringify(response)),
    ).catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
  }

  createCalendar() {
    CapacitorCalendar.createCalendar({title: "Test Calendar"}).then((response) => {
        this.storeService.dispatchLog(JSON.stringify(response))
        this.lastCreatedCalendar = response.result
      },
    ).catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
  }

  createEventNonRecurring() {
    const now = Date.now();
    const event = {
      calendarId: this.lastCreatedCalendar,
      title: 'Capacitor Calendar',
      startDate: now,
      endDate: now + 2 * 60 * 60 * 1000,
      location: 'Capacitor Calendar',
      isAllDay: false,
      alertOffsetInMinutes: [0, 1440],
      url: 'https://capacitor-calendar.pages.dev',
      notes: 'A CapacitorJS plugin',
    }

    CapacitorCalendar.createEvent(event).then((response) => {
      this.storeService.dispatchLog(JSON.stringify(response))
      this.lastCreatedEvent = response.result
    }).catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
  }

  createEventRecurring() {
    const now = Date.now();
    const event = {
      calendarId: this.lastCreatedCalendar,
      title: 'Capacitor Calendar',
      startDate: now,
      endDate: now + 2 * 60 * 60 * 1000,
      location: 'Capacitor Calendar',
      isAllDay: false,
      alertOffsetInMinutes: [0, 1440],
      url: 'https://capacitor-calendar.pages.dev',
      notes: 'A CapacitorJS plugin',
      recurrence: {
        frequency: RecurrenceFrequency.DAILY,
        interval: 1
      }
    }
    CapacitorCalendar.createEvent(event).then((response) => {
      this.storeService.dispatchLog(JSON.stringify(response))
      this.lastCreatedRecurringEvent = response.result
    }).catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
  }

  modifyEventNonRecurring() {
    const now = Date.now();
    CapacitorCalendar.modifyEvent({
      id: this.lastCreatedEvent,
      update: {
        title: "Updated Title",
        startDate: now + (60 * 60 * 1000),
        endDate: now + (2 * 60 * 60 * 1000),
        calendarId: this.lastCreatedCalendar
      }
    }).then()
      .catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
  }

  modifyEventRecurring() {
    const now = Date.now();
    CapacitorCalendar.modifyEvent({
      id: this.lastCreatedRecurringEvent,
      update: {
        title: "Updated Title",
        startDate: now + (60 * 60 * 1000),
        endDate: now + (2 * 60 * 60 * 1000),
        calendarId: this.lastCreatedCalendar,
        recurrence: {
          frequency: RecurrenceFrequency.WEEKLY,
          interval: 1
        }
      },
      span: EventSpan.THIS_AND_FUTURE_EVENTS
    }).then()
      .catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
  }

  deleteEventNonRecurring() {
    CapacitorCalendar.deleteEventById({id: this.lastCreatedEvent, span: EventSpan.THIS_EVENT})
      .then((response) =>
        this.storeService.dispatchLog(JSON.stringify(response)),
      ).catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
    ;
  }

  deleteEventRecurring() {
    CapacitorCalendar.deleteEventById({id: this.lastCreatedRecurringEvent, span: EventSpan.THIS_AND_FUTURE_EVENTS})
      .then((response) =>
        this.storeService.dispatchLog(JSON.stringify(response)),
      ).catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
    ;
  }

  deleteAllEvents() {
    CapacitorCalendar.deleteEventsById({ids: [this.lastCreatedEvent, this.lastCreatedRecurringEvent]})
      .then((response) =>
        this.storeService.dispatchLog(JSON.stringify(response)),
      ).catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
  }

  deleteCalendar() {
    CapacitorCalendar.deleteCalendar({id: this.lastCreatedCalendar}).then((response) =>
      this.storeService.dispatchLog(JSON.stringify(response)),
    ).catch((error) => this.storeService.dispatchLog(JSON.stringify(error)));
  }

}
