export default class ConferenceParticipant {
  
  public externalId: string | undefined;
  public name: string;
  public avatarUrl: string | undefined;

  constructor(externalId: string, name: string, avatarUrl: string | undefined) {
    this.externalId = externalId;
    this.name = name;
    this.avatarUrl = avatarUrl;
  }

}
